-- V2: import wyciągów bankowych.
--
-- Ścieżka jest dwuetapowa: wgranie pliku tworzy partię i wiersze, ale żadnej
-- transakcji. Transakcje powstają dopiero przy zatwierdzeniu, w jednej
-- transakcji bazodanowej. Dzięki temu podgląd pokazuje, co się stanie, zanim
-- cokolwiek się stanie.

create table finance.import_batch (
    id          bigserial primary key,
    account_id  bigint       not null references finance.account(id),
    file_name   varchar(255) not null,
    -- SHA-256 zawartości pliku: ten sam plik wgrany drugi raz odpada tutaj,
    -- zanim ktokolwiek go sparsuje
    file_hash   char(64)     not null,
    status      varchar(20)  not null check (status in ('PARSED','COMMITTED')),
    period_from date,
    period_to   date,
    -- salda z nagłówka i stopki wyciągu; służą wyłącznie do uzgodnienia
    opening_balance_minor bigint,
    closing_balance_minor bigint,
    row_count    integer     not null default 0,
    uploaded_at  timestamptz not null default now(),
    committed_at timestamptz,

    constraint ux_import_batch_file unique (account_id, file_hash),
    constraint ck_batch_committed check (
        (status = 'COMMITTED') = (committed_at is not null)
    ),
    constraint ck_batch_period check (
        period_from is null or period_to is null or period_from <= period_to
    )
);
create index ix_import_batch_account on finance.import_batch (account_id, uploaded_at desc);

create table finance.import_row (
    id       bigserial primary key,
    batch_id bigint  not null references finance.import_batch(id) on delete cascade,
    -- pozycja w pliku, liczona od zera; porządkuje podgląd i wchodzi do
    -- klucza deduplikacji dla wierszy bez referencji bankowej
    ordinal  integer not null,
    booked_on    date   not null,
    -- ZE ZNAKIEM, dokładnie jak na wyciągu. Zamiana znaku na typ transakcji
    -- następuje w jednym miejscu, przy zatwierdzeniu.
    amount_minor bigint not null,
    currency     char(3) not null references finance.currency(code),
    original_amount_minor bigint,
    original_currency     char(3) references finance.currency(code),
    description   varchar(255),
    counterparty  varchar(255),
    bank_reference varchar(100),
    -- SHA-256; z referencją bankową liczony z niej, bez niej z daty, kwoty,
    -- znormalizowanego opisu i numeru kolejnego wśród identycznych wierszy
    dedup_key     char(64)    not null,
    status        varchar(20) not null check (status in ('NEW','DUPLICATE','COMMITTED')),
    suggested_category_id bigint references finance.category(id),

    constraint ux_import_row_ordinal unique (batch_id, ordinal),
    constraint ck_row_original check (
        (original_amount_minor is null) = (original_currency is null)
    )
);

-- Wiersz uznany za duplikat świadomie NIE rezerwuje klucza: to ten sam wpis,
-- który rezerwuje go już z poprzedniego importu. Bez warunku częściowego drugi
-- wgrany plik z nachodzącym zakresem dat wywalałby się na unikalności zamiast
-- spokojnie oznaczyć wiersze jako duplikaty.
create unique index ux_import_row_dedup on finance.import_row (dedup_key)
    where status <> 'DUPLICATE';
create index ix_import_row_batch on finance.import_row (batch_id, ordinal);

-- Ślad pochodzenia transakcji. Jeden wiersz wyciągu daje najwyżej jedną
-- transakcję, stąd unikalność.
alter table finance.transaction
    add column import_row_id bigint references finance.import_row(id);
create unique index ux_tx_import_row on finance.transaction (import_row_id)
    where import_row_id is not null;
