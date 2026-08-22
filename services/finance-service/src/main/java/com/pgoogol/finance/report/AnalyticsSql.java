package com.pgoogol.finance.report;

import lombok.experimental.UtilityClass;

/**
 * Zapytania raportów analitycznych. Osobno od {@link ReportSql}, bo tamten
 * odpowiada za trzy zestawienia podstawowe i obie klasy razem przekroczyłyby
 * rozsądną długość.
 *
 * <p>Reguła {@code TRANSFER} obowiązuje tu tak samo — każde zapytanie liczące
 * wydatki albo przychody zaczyna od jego wykluczenia.</p>
 */
@UtilityClass
public class AnalyticsSql {

    /** Kategorie z filtra wraz z poddrzewem — to samo, co w raportach podstawowych. */
    private static final String SELECTED_CATEGORIES = """
        with recursive selected_category as (
            select c.id from finance.category c where c.id in (:categoryIds)
            union all
            select c.id from finance.category c
              join selected_category s on c.parent_id = s.id
        ),
        """;

    /** Przepływy z zachowaną datą księgowania — potrzebną do podziału na okna. */
    private static final String DATED_FLOWS = """
        flows as (
            select t.booked_on, t.category_id, t.base_amount_minor
              from finance.transaction t
              left join finance.category c on c.id = t.category_id
             where t.type <> 'TRANSFER'
               and t.booked_on between :windowFrom and :windowTo
               and (:allAccounts = true or t.account_id in (:accountIds))
               and (:allCategories = true
                    or t.category_id in (select id from selected_category))
               and (cast(:direction as text) is null
                    or c.direction = cast(:direction as text))
        )
        """;

    /**
     * Trzy okna liczone jednym przebiegiem po tych samych przepływach: bieżące,
     * poprzednie i dwunastomiesięczne. Trzy osobne zapytania czytałyby te same
     * wiersze trzy razy.
     */
    static final String COMPARISON = SELECTED_CATEGORIES + DATED_FLOWS + """
        , current_period as (
            select f.category_id, sum(f.base_amount_minor) as amount
              from flows f where f.booked_on between :from and :to
             group by f.category_id
        ), previous_period as (
            select f.category_id, sum(f.base_amount_minor) as amount
              from flows f where f.booked_on between :previousFrom and :previousTo
             group by f.category_id
        ), average_period as (
            select f.category_id,
                   round(sum(f.base_amount_minor)::numeric / 12) as amount
              from flows f where f.booked_on between :averageFrom and :averageTo
             group by f.category_id
        )
        select c.id as category_id, c.name as category_name,
               coalesce(cur.amount, 0) as current_minor,
               coalesce(prev.amount, 0) as previous_minor,
               coalesce(avg12.amount, 0) as monthly_average_minor
          from finance.category c
          left join current_period cur on cur.category_id = c.id
          left join previous_period prev on prev.category_id = c.id
          left join average_period avg12 on avg12.category_id = c.id
         where cur.amount is not null or prev.amount is not null or avg12.amount is not null
         order by coalesce(cur.amount, 0) desc, c.name asc
        """;

    static final String TOP_EXPENSES = SELECTED_CATEGORIES + """
        top_expense as (
            select t.id, t.booked_on, t.base_amount_minor, t.description, t.counterparty,
                   t.category_id, t.account_id
              from finance.transaction t
             where t.type = 'EXPENSE'
               and t.booked_on between :from and :to
               and (:allAccounts = true or t.account_id in (:accountIds))
               and (:allCategories = true
                    or t.category_id in (select id from selected_category))
        )
        select e.id as transaction_id, e.booked_on,
               e.base_amount_minor as amount_minor, e.description, e.counterparty,
               c.name as category_name, a.name as account_name
          from top_expense e
          join finance.account a on a.id = e.account_id
          left join finance.category c on c.id = e.category_id
         order by e.base_amount_minor desc, e.id asc
         limit :limit
        """;

    static final String TOP_COUNTERPARTIES = SELECTED_CATEGORIES + """
        named_expense as (
            select t.counterparty, t.base_amount_minor
              from finance.transaction t
             where t.type = 'EXPENSE'
               and t.counterparty is not null
               and length(btrim(t.counterparty)) > 0
               and t.booked_on between :from and :to
               and (:allAccounts = true or t.account_id in (:accountIds))
               and (:allCategories = true
                    or t.category_id in (select id from selected_category))
        )
        select btrim(e.counterparty) as counterparty,
               sum(e.base_amount_minor) as amount_minor,
               count(*) as transaction_count
          from named_expense e
         group by btrim(e.counterparty)
         order by amount_minor desc, transaction_count desc, counterparty asc
         limit :limit
        """;

    /**
     * Koszt stały to wydatek <b>powiązany z pozycją terminarza</b>, czyli taki,
     * który ktoś potwierdził jako płatność rachunku cyklicznego. Zgadywanie po
     * kategorii dawałoby liczbę wyglądającą wiarygodnie i nieprawdziwą.
     */
    static final String FIXED_VS_VARIABLE = """
        select date_trunc(:granularity, t.booked_on::timestamp)::date as period,
               coalesce(sum(t.base_amount_minor) filter (where o.id is not null), 0)
                   as fixed_minor,
               coalesce(sum(t.base_amount_minor) filter (where o.id is null), 0)
                   as variable_minor
          from finance.transaction t
          left join finance.scheduled_occurrence o on o.transaction_id = t.id
         where t.type = 'EXPENSE'
           and t.booked_on between :from and :to
           and (:allAccounts = true or t.account_id in (:accountIds))
         group by 1
         order by 1
        """;

    /**
     * Pozycje terminarza czekające na zapłatę do wskazanego dnia — razem
     * z przeterminowanymi, bo one nie przestają być zobowiązaniem przez to,
     * że termin minął.
     */
    static final String UPCOMING = """
        select o.id as occurrence_id, r.id as rule_id, r.name as rule_name,
               a.id as account_id, a.name as account_name, r.type as rule_type,
               o.due_date, o.expected_amount_minor, o.currency,
               (o.due_date < :today) as overdue
          from finance.scheduled_occurrence o
          join finance.recurring_rule r on r.id = o.rule_id
          join finance.account a on a.id = r.account_id
         where o.status = 'PENDING'
           and o.due_date <= :horizonTo
           and (:allAccounts = true or r.account_id in (:accountIds))
         order by o.due_date asc, o.id asc
        """;

    /**
     * Dzisiejsze saldo każdego niezarchiwizowanego konta, w walucie konta.
     * Wzór pożyczony z {@link ReportSql} — jedno miejsce na całą aplikację.
     */
    static final String ACCOUNT_BALANCES_NOW = """
        select a.id as account_id, a.name as account_name, a.currency,
        """ + ReportSql.balanceAsOf("cast(:asOf as date)") + """
               as balance_minor
          from finance.account a
         where a.archived = false
           and (:allAccounts = true or a.id in (:accountIds))
         order by a.name asc, a.id asc
        """;

    /** Kwoty kategorii w podziale na miesiące jednego roku; oś pivotuje aplikacja. */
    static final String YEARLY_MATRIX = SELECTED_CATEGORIES + DATED_FLOWS + """
        select f.category_id, c.name as category_name,
               extract(month from f.booked_on)::int as month_index,
               sum(f.base_amount_minor) as amount_minor
          from flows f
          join finance.category c on c.id = f.category_id
         group by f.category_id, c.name, extract(month from f.booked_on)
         order by c.name asc, month_index asc
        """;
}
