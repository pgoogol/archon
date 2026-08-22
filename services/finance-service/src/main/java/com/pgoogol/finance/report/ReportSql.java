package com.pgoogol.finance.report;

import lombok.experimental.UtilityClass;

/**
 * Zapytania raportowe w jednym miejscu.
 *
 * <p>Najważniejszy jest {@link #FLOWS} — wspólne wycięcie przepływów, doklejane
 * przez każdy raport przychodów i wydatków. Siedzą w nim dwie decyzje, których
 * <b>nie wolno powtórzyć jedenaście razy</b>, bo za jedenastym razem któraś
 * wypadnie:</p>
 *
 * <ul>
 *   <li><b>{@code TRANSFER} jest wykluczony</b> — przelew na własne konto
 *       oszczędnościowe nie jest wydatkiem, a policzony zawyżałby naraz
 *       wydatki i przychody;</li>
 *   <li><b>kwota to {@code base_amount_minor}</b>, czyli wartość w walucie
 *       bazowej po kursie zapisanym w chwili powstania transakcji. Żaden raport
 *       nie mnoży kwoty historycznej przez kurs bieżący.</li>
 * </ul>
 *
 * <p>Fragmenty sklejamy stałymi, a nie literałami — każdy kończy się znakiem
 * końca linii, więc doklejenie kolejnego nie zlepia dwóch słów kluczowych.</p>
 */
@UtilityClass
public class ReportSql {

    /**
     * Kategorie wskazane w filtrze wraz z całym poddrzewem. Filtr po „Jedzeniu"
     * bez podkategorii pokazywałby zero w drzewie, w którym wszystko siedzi
     * w liściach.
     */
    private static final String SELECTED_CATEGORIES = """
        with recursive selected_category as (
            select c.id from finance.category c where c.id in (:categoryIds)
            union all
            select c.id from finance.category c
              join selected_category s on c.parent_id = s.id
        ),
        """;

    /**
     * Każda kategoria ze wszystkimi swoimi przodkami, ona sama włącznie.
     * Po tym rozwija się sumy w górę drzewa jednym złączeniem, zamiast liczyć
     * poddrzewo osobno dla każdego węzła.
     */
    private static final String ANCESTRY = """
        ancestry as (
            select c.id as category_id, c.id as ancestor_id from finance.category c
            union all
            select a.category_id, c.parent_id
              from ancestry a join finance.category c on c.id = a.ancestor_id
             where c.parent_id is not null
        ),
        """;

    /** Przepływy w oknie raportu: bez transferów, w walucie bazowej. */
    private static final String FLOWS = """
        flows as (
            select date_trunc(:granularity, t.booked_on::timestamp)::date as period,
                   t.type, t.category_id, t.base_amount_minor
              from finance.transaction t
              left join finance.category c on c.id = t.category_id
             where t.type <> 'TRANSFER'
               and t.booked_on between :from and :to
               and (:allAccounts = true or t.account_id in (:accountIds))
               and (:allCategories = true
                    or t.category_id in (select id from selected_category))
               and (cast(:direction as text) is null
                    or c.direction = cast(:direction as text))
        )
        """;

    static final String BY_CATEGORY = SELECTED_CATEGORIES + ANCESTRY + FLOWS + """
        , rolled as (
            select f.period, a.ancestor_id as category_id,
                   sum(f.base_amount_minor) as amount_minor,
                   count(*) as transaction_count
              from flows f join ancestry a on a.category_id = f.category_id
             group by f.period, a.ancestor_id
        ), own as (
            select f.period, f.category_id,
                   sum(f.base_amount_minor) as own_amount_minor
              from flows f group by f.period, f.category_id
        )
        select r.period, r.category_id, c.name as category_name,
               c.parent_id as parent_category_id, r.amount_minor,
               coalesce(o.own_amount_minor, 0) as own_amount_minor,
               r.transaction_count
          from rolled r
          join finance.category c on c.id = r.category_id
          left join own o on o.period = r.period and o.category_id = r.category_id
         order by r.period asc, c.name asc
        """;

    /**
     * Sumy okresów liczone z surowych przepływów, nie z rozwiniętego drzewa —
     * inaczej kwota z podkategorii weszłaby do sumy drugi raz przez rodzica.
     */
    static final String BY_CATEGORY_TOTALS = SELECTED_CATEGORIES + FLOWS + """
        select f.period, sum(f.base_amount_minor) as amount_minor
          from flows f
         group by f.period
         order by f.period asc
        """;

    static final String CASHFLOW = SELECTED_CATEGORIES + FLOWS + """
        select f.period,
               coalesce(sum(f.base_amount_minor) filter (where f.type = 'INCOME'), 0)
                   as income_minor,
               coalesce(sum(f.base_amount_minor) filter (where f.type = 'EXPENSE'), 0)
                   as expense_minor
          from flows f
         group by f.period
         order by f.period asc
        """;

    /**
     * Saldo konta na koniec każdego okresu, w walucie konta.
     *
     * <p>Wzór jest ten sam co w {@code AccountRepository.findBalanceMinor}:
     * saldo otwarcia plus ruchy własne, plus przychodzące transfery po stronie
     * konta docelowego. Transfery <b>wchodzą</b> tutaj świadomie — wykluczamy
     * je z zestawień wydatków, ale saldo bez nich byłoby po prostu błędne.</p>
     */
    static final String BALANCES = """
        with periods as (
            select generate_series(date_trunc(:granularity, cast(:from as timestamp)),
                                   date_trunc(:granularity, cast(:to as timestamp)),
                                   cast(concat('1 ', :granularity) as interval))::date as period
        ), chosen_account as (
            select a.id, a.name, a.currency, a.opening_balance_minor, a.opening_balance_on
              from finance.account a
             where (:allAccounts = true or a.id in (:accountIds))
        )
        select p.period, a.id as account_id, a.name as account_name, a.currency,
               case when a.opening_balance_on <= period_end.value
                    then a.opening_balance_minor else 0 end
               + coalesce((select sum(case t.type when 'INCOME' then t.amount_minor
                                                  else -t.amount_minor end)
                             from finance.transaction t
                            where t.account_id = a.id
                              and t.booked_on >= a.opening_balance_on
                              and t.booked_on <= period_end.value), 0)
               + coalesce((select sum(coalesce(t.to_amount_minor, t.amount_minor))
                             from finance.transaction t
                            where t.to_account_id = a.id
                              and t.type = 'TRANSFER'
                              and t.booked_on >= a.opening_balance_on
                              and t.booked_on <= period_end.value), 0)
               as balance_minor
          from periods p
          cross join chosen_account a
          cross join lateral (
              select (p.period + cast(concat('1 ', :granularity) as interval)
                      - interval '1 day')::date as value
          ) as period_end
         order by p.period asc, a.name asc, a.id asc
        """;
}
