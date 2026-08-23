-- V1: fundament modułu finansowego — waluty, kursy, konta, kategorie, transakcje.
--
-- Kwoty wszędzie jako bigint w jednostkach podrzędnych waluty. Liczba miejsc
-- po przecinku NIE jest stałą w kodzie: PLN i EUR mają 2, JPY ma 0, TND ma 3 —
-- dlatego siedzi w kolumnie currency.minor_unit.

create schema if not exists finance;

create table finance.currency (
    code        char(3)  primary key,
    name        varchar(50) not null,
    minor_unit  smallint not null check (minor_unit between 0 and 4)
);

insert into finance.currency (code, name, minor_unit) values
    ('PLN', 'złoty polski',     2),
    ('EUR', 'euro',             2),
    ('USD', 'dolar amerykański', 2),
    ('GBP', 'funt szterling',   2),
    ('CHF', 'frank szwajcarski', 2),
    ('JPY', 'jen japoński',     0);

-- Kurs trzymamy lokalnie i historycznie. NBP nie publikuje tabel w weekendy
-- i święta, więc dat bywa mniej niż dni — reguła wyboru kursu to "najnowszy
-- o dacie <= data księgowania".
create table finance.exchange_rate (
    code       char(3)       not null references finance.currency(code),
    rate_date  date          not null,
    rate       numeric(18,8) not null check (rate > 0),   -- ile PLN za 1 jednostkę waluty
    source     varchar(20)   not null check (source in ('NBP','MANUAL')),
    primary key (code, rate_date)
);

create table finance.account (
    id                     bigserial primary key,
    name                   varchar(100) not null,
    type                   varchar(20)  not null check (type in ('BANK','CASH','CARD')),
    currency               char(3)      not null references finance.currency(code),
    iban                   varchar(34),
    opening_balance_minor  bigint       not null default 0,
    opening_balance_on     date         not null,
    archived               boolean      not null default false
);

create table finance.category (
    id         bigserial primary key,
    parent_id  bigint references finance.category(id),
    name       varchar(100) not null,
    direction  varchar(10)  not null check (direction in ('EXPENSE','INCOME')),
    archived   boolean      not null default false
);
-- Nazwa unikalna w obrębie rodzica; korzenie dzielą sztuczny rodzic 0, bo
-- w unikalnym indeksie NULL-e nie kolidują ze sobą i "Jedzenie" dałoby się
-- założyć dwa razy.
create unique index ux_category_name on finance.category (coalesce(parent_id, 0), lower(name));

-- Kwota jest ZAWSZE dodatnia, kierunek wynika z type. TRANSFER jest osobnym
-- typem, bo przelew na własne konto oszczędnościowe nie jest wydatkiem —
-- bez tego każda taka operacja zawyżałaby naraz wydatki i przychody.
create table finance.transaction (
    id                     bigserial primary key,
    type                   varchar(10)   not null check (type in ('EXPENSE','INCOME','TRANSFER')),
    booked_on              date          not null,
    amount_minor           bigint        not null check (amount_minor > 0),  -- w walucie konta
    currency               char(3)       not null references finance.currency(code),
    base_amount_minor      bigint        not null,                           -- w walucie bazowej
    fx_rate                numeric(18,8) not null,
    fx_rate_date           date          not null,
    original_amount_minor  bigint,        -- kwota z karty/terminala w walucie obcej
    original_currency      char(3) references finance.currency(code),
    account_id             bigint        not null references finance.account(id),
    to_account_id          bigint references finance.account(id),
    to_amount_minor        bigint,        -- transfer między kontami o różnych walutach
    category_id            bigint references finance.category(id),
    description            varchar(255),
    counterparty           varchar(255),
    created_at             timestamptz   not null default now(),

    constraint ck_tx_shape check (
        (type =  'TRANSFER' and category_id is null
             and to_account_id is not null and to_account_id <> account_id)
     or (type <> 'TRANSFER' and to_account_id is null and to_amount_minor is null
             and category_id is not null)
    ),
    constraint ck_tx_original check (
        (original_amount_minor is null) = (original_currency is null)
    ),
    constraint ck_tx_base_rate check (
        (currency = 'PLN' and fx_rate = 1) or currency <> 'PLN'
    )
);
create index ix_tx_booked_on on finance.transaction (booked_on);
create index ix_tx_account   on finance.transaction (account_id);
-- konto docelowe transferu wchodzi do salda drugiej strony, więc filtruje się
-- po nim tak samo często jak po account_id
create index ix_tx_to_account on finance.transaction (to_account_id) where to_account_id is not null;
create index ix_tx_category   on finance.transaction (category_id)   where category_id is not null;
