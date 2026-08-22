-- V3: rachunki cykliczne i terminarz płatności.
--
-- Reguła opisuje, co i kiedy ma przyjść. Terminarz to jej rozwinięcie na
-- konkretne daty — generowane z wyprzedzeniem, żeby dało się je zobaczyć
-- i odhaczyć, a nie dopiero policzyć w momencie pytania.

create table finance.recurring_rule (
    id           bigserial    primary key,
    name         varchar(100) not null,
    account_id   bigint       not null references finance.account(id),
    category_id  bigint       not null references finance.category(id),
    type         varchar(10)  not null check (type in ('EXPENSE','INCOME')),
    -- kwota oczekiwana; faktyczna trafia na pozycję terminarza przy płatności,
    -- bo rachunek za prąd rzadko wychodzi co do grosza tak samo
    amount_minor bigint       not null check (amount_minor > 0),
    currency     char(3)      not null references finance.currency(code),
    frequency    varchar(10)  not null check (frequency in ('MONTHLY','QUARTERLY','YEARLY')),
    -- dzień większy niż długość miesiąca przesuwa się na jego ostatni dzień;
    -- rachunek z 31 w lutym wypada 28 albo 29
    day_of_month smallint     not null check (day_of_month between 1 and 31),
    starts_on    date         not null,
    ends_on      date,
    -- fragment opisu, po którym import rozpozna tę płatność (etap 6)
    match_pattern varchar(255),
    active       boolean      not null default true,
    version      bigint       not null default 0,

    constraint ck_rule_period check (ends_on is null or ends_on >= starts_on)
);
create index ix_rule_active on finance.recurring_rule (active) where active;

create table finance.scheduled_occurrence (
    id      bigserial primary key,
    rule_id bigint    not null references finance.recurring_rule(id) on delete cascade,
    due_date date     not null,
    expected_amount_minor bigint  not null check (expected_amount_minor > 0),
    currency              char(3) not null references finance.currency(code),
    -- OVERDUE świadomie NIE jest tu wartością: to PENDING z terminem
    -- wcześniejszym niż dziś, wyliczane przy odczycie. Przechowywane
    -- wymagałoby joba przepisującego statusy o północy — i rozjeżdżałoby się
    -- za każdym razem, gdy ten job nie wstanie.
    status  varchar(10) not null check (status in ('PENDING','PAID','SKIPPED')),
    paid_on date,
    paid_amount_minor bigint,
    transaction_id    bigint references finance.transaction(id),

    -- jedna pozycja na regułę i termin; to na niej opiera się idempotencja
    -- generatora
    constraint ux_occurrence unique (rule_id, due_date),
    constraint ck_occurrence_paid check (
        (status = 'PAID') = (paid_on is not null)
            and (status = 'PAID') = (paid_amount_minor is not null)
    )
);
create index ix_occurrence_due on finance.scheduled_occurrence (due_date, status);
create unique index ux_occurrence_transaction on finance.scheduled_occurrence (transaction_id)
    where transaction_id is not null;
