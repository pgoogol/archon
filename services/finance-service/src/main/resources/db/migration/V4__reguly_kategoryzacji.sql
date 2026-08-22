-- V4: automatyzacja importu — reguły kategoryzacji i dowiązanie do terminarza.
--
-- Wszystko, co powstaje z tych reguł, jest SUGESTIĄ. Wiersz wyciągu dostaje
-- propozycję kategorii i propozycję pozycji terminarza, ale żadna z nich nie
-- staje się faktem bez potwierdzenia w podglądzie. Automatyczne oznaczenie
-- rachunku jako opłaconego byłoby najgorszym rodzajem cichego błędu w tej
-- aplikacji: pieniądze zgadzałyby się na ekranie i nie zgadzały w banku.

create table finance.category_rule (
    id          bigserial    primary key,
    -- fragment tekstu; dopasowanie jest zawieraniem, nie wyrażeniem regularnym
    pattern     varchar(255) not null check (length(btrim(pattern)) > 0),
    match_field varchar(20)  not null
        check (match_field in ('DESCRIPTION', 'COUNTERPARTY', 'ANY')),
    category_id bigint       not null references finance.category(id),
    -- niższa liczba wygrywa; przy równej decyduje id, żeby wynik był powtarzalny
    priority    int          not null default 100,
    active      boolean      not null default true
);
create index ix_category_rule_order on finance.category_rule (priority asc, id asc)
    where active;

-- Propozycja rozliczenia rachunku cyklicznego tym wierszem wyciągu.
-- Bez ograniczenia unikalności: dwa wiersze mogą wskazywać tę samą pozycję,
-- dopóki nikt żadnego z nich nie potwierdzi.
alter table finance.import_row
    add column suggested_occurrence_id bigint references finance.scheduled_occurrence(id);
create index ix_import_row_occurrence on finance.import_row (suggested_occurrence_id)
    where suggested_occurrence_id is not null;
