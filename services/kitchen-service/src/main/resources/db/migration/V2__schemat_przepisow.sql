-- V2: schemat przepisów — słowniki, katalog składników, przepis rozbity na
-- pojedyncze wiersze oraz dziennik zmian.
--
-- Dwie rzeczy warto przeczytać, zanim zmienisz cokolwiek niżej:
--
-- 1. Stan bieżący przepisu żyje w zwykłych tabelach (recipe, recipe_ingredient,
--    recipe_step). Historia to osobny dziennik (recipe_revision + recipe_change),
--    który zapisuje WYŁĄCZNIE zmienione pola. Starszą wersję odtwarza się,
--    cofając zmiany od stanu bieżącego — nie ma kopii całego przepisu.
-- 2. Tożsamość wiersza przeżywa edycje. Zmiana ilości to update pola, a nie
--    skasowanie i wstawienie wiersza — inaczej dziennik mówiłby tylko tyle, że
--    „coś zniknęło i coś się pojawiło".

create schema if not exists kitchen;

-- ── Słowniki ────────────────────────────────────────────────────────────────

-- Jednostki miary. `to_base` przelicza w obrębie rodzaju (gram albo mililitr);
-- dla sztuk i szczypt jest puste, bo nie mają wspólnej miary. Przeliczenia
-- między masą a objętością zależą od produktu i nie należą do słownika.
create table kitchen.unit (
    id       bigserial     primary key,
    code     varchar(30)   not null unique,
    name     varchar(50)   not null,
    kind     varchar(10)   not null check (kind in ('MASS','VOLUME','COUNT','OTHER')),
    to_base  numeric(12,4) check (to_base is null or to_base > 0)
);

insert into kitchen.unit (code, name, kind, to_base) values
    ('g',           'gram',        'MASS',   1),
    ('kg',          'kilogram',    'MASS',   1000),
    ('dag',         'dekagram',    'MASS',   10),
    ('ml',          'mililitr',    'VOLUME', 1),
    ('l',           'litr',        'VOLUME', 1000),
    ('lyzka',       'łyżka',       'VOLUME', 15),
    ('lyzeczka',    'łyżeczka',    'VOLUME', 5),
    ('szklanka',    'szklanka',    'VOLUME', 250),
    ('szt',         'sztuka',      'COUNT',  null),
    ('opakowanie',  'opakowanie',  'COUNT',  null),
    ('puszka',      'puszka',      'COUNT',  null),
    ('sloik',       'słoik',       'COUNT',  null),
    ('plaster',     'plaster',     'COUNT',  null),
    ('zabek',       'ząbek',       'COUNT',  null),
    ('peczek',      'pęczek',      'COUNT',  null),
    ('kostka',      'kostka',      'COUNT',  null),
    ('listek',      'listek',      'COUNT',  null),
    ('galazka',     'gałązka',     'COUNT',  null),
    ('garsc',       'garść',       'OTHER',  null),
    ('szczypta',    'szczypta',    'OTHER',  null),
    ('kropla',      'kropla',      'OTHER',  null),
    ('do_smaku',    'do smaku',    'OTHER',  null);

-- Pięć słowników prostych ma ten sam kształt, ale osobne tabele: dzięki temu
-- klucz obcy przepisu do kuchni nie może wskazać diety.
create table kitchen.cuisine (
    id               bigserial    primary key,
    name             varchar(100) not null,
    name_normalized  varchar(100) not null unique
);

create table kitchen.recipe_category (
    id               bigserial    primary key,
    name             varchar(100) not null,
    name_normalized  varchar(100) not null unique
);

create table kitchen.diet (
    id               bigserial    primary key,
    name             varchar(100) not null,
    name_normalized  varchar(100) not null unique
);

create table kitchen.tag (
    id               bigserial    primary key,
    name             varchar(100) not null,
    name_normalized  varchar(100) not null unique
);

create table kitchen.equipment (
    id               bigserial    primary key,
    name             varchar(100) not null,
    name_normalized  varchar(100) not null unique
);

insert into kitchen.cuisine (name, name_normalized) values
    ('polska','polska'), ('włoska','wloska'), ('francuska','francuska'),
    ('hiszpańska','hiszpanska'), ('grecka','grecka'), ('bałkańska','balkanska'),
    ('turecka','turecka'), ('bliskowschodnia','bliskowschodnia'), ('indyjska','indyjska'),
    ('tajska','tajska'), ('wietnamska','wietnamska'), ('chińska','chinska'),
    ('japońska','japonska'), ('koreańska','koreanska'), ('meksykańska','meksykanska'),
    ('amerykańska','amerykanska'), ('brytyjska','brytyjska'), ('niemiecka','niemiecka'),
    ('ukraińska','ukrainska'), ('gruzińska','gruzinska'), ('skandynawska','skandynawska'),
    ('międzynarodowa','miedzynarodowa');

insert into kitchen.recipe_category (name, name_normalized) values
    ('śniadanie','sniadanie'), ('zupa','zupa'), ('danie główne','danie glowne'),
    ('sałatka','salatka'), ('przekąska','przekaska'), ('deser','deser'),
    ('wypiek','wypiek'), ('napój','napoj'), ('sos i dodatek','sos i dodatek'),
    ('przetwór','przetwor'), ('dla dzieci','dla dzieci');

insert into kitchen.diet (name, name_normalized) values
    ('wegetariańska','wegetarianska'), ('wegańska','weganska'),
    ('bezglutenowa','bezglutenowa'), ('bez laktozy','bez laktozy'),
    ('bez cukru','bez cukru'), ('keto','keto'),
    ('niskokaloryczna','niskokaloryczna'), ('wysokobiałkowa','wysokobialkowa');

-- ── Katalog składników ──────────────────────────────────────────────────────

-- Nazwa kanoniczna w liczbie pojedynczej i mianowniku („cebula", nie „cebule").
-- `status` odróżnia pozycje potwierdzone od tych, które wpadły z importu i czekają
-- na przejrzenie; `merged_into_id` zostaje po scaleniu duplikatu, żeby stare
-- odwołania miały dokąd prowadzić.
create table kitchen.ingredient (
    id               bigserial    primary key,
    name             varchar(200) not null,
    name_normalized  varchar(200) not null unique,
    category         varchar(50),
    default_unit_id  bigint       references kitchen.unit(id),
    status           varchar(20)  not null default 'NEW' check (status in ('NEW','VERIFIED')),
    merged_into_id   bigint       references kitchen.ingredient(id),
    lock_version     bigint       not null default 0,
    created_at       timestamptz  not null default now()
);

-- Synonimy prowadzące do tej samej pozycji katalogu: „cebulka", „onion",
-- „cebula czerwona". Unikalność globalna, bo alias ma wskazywać jednoznacznie.
create table kitchen.ingredient_alias (
    id                bigserial    primary key,
    ingredient_id     bigint       not null references kitchen.ingredient(id) on delete cascade,
    alias             varchar(200) not null,
    alias_normalized  varchar(200) not null unique,
    language          varchar(5)
);

create index ix_ingredient_alias_ingredient on kitchen.ingredient_alias (ingredient_id);
-- Dopasowanie z literówką („cukini" → „cukinia") idzie po podobieństwie trigramów.
create index ix_ingredient_alias_trgm on kitchen.ingredient_alias using gin (alias_normalized gin_trgm_ops);
create index ix_ingredient_name_trgm on kitchen.ingredient using gin (name_normalized gin_trgm_ops);

-- ── Źródło przepisu ─────────────────────────────────────────────────────────

-- To, co przyszło z internetu, z aparatu albo z czatu — niezmienne. Kolumny
-- `raw_text` i `json_ld` wypełnia import; przepis pisany ręcznie ma tu kind
-- MANUAL albo nie ma źródła wcale.
create table kitchen.recipe_source (
    id               bigserial   primary key,
    kind             varchar(20) not null check (kind in ('URL','TEXT','FILES','CHAT','MANUAL')),
    url              text,
    url_normalized   text,
    site_name        varchar(200),
    author           varchar(200),
    source_language  varchar(5),
    raw_text         text,
    json_ld          jsonb,
    fetched_at       timestamptz,
    created_at       timestamptz not null default now()
);

-- Ten sam adres nie ma tworzyć drugiego źródła; adresy bez URL-a (tekst, pliki)
-- nie kolidują ze sobą, bo NULL-e w indeksie unikalnym się nie liczą.
create unique index ux_recipe_source_url on kitchen.recipe_source (url_normalized)
    where url_normalized is not null;

-- ── Przepis ────────────────────────────────────────────────────────────────

create table kitchen.recipe (
    id                   bigserial    primary key,
    title                varchar(300) not null,
    description          text,
    servings_amount      numeric(8,2) check (servings_amount is null or servings_amount > 0),
    servings_unit        varchar(50),
    prep_minutes         integer      check (prep_minutes is null or prep_minutes >= 0),
    cook_minutes         integer      check (cook_minutes is null or cook_minutes >= 0),
    total_minutes        integer      check (total_minutes is null or total_minutes >= 0),
    cuisine_id           bigint       references kitchen.cuisine(id),
    category_id          bigint       references kitchen.recipe_category(id),
    difficulty           varchar(10)  check (difficulty is null or difficulty in ('EASY','MEDIUM','HARD')),
    source_id            bigint       references kitchen.recipe_source(id),
    current_revision_no  integer      not null default 0,
    status               varchar(20)  not null default 'ACTIVE' check (status in ('ACTIVE','ARCHIVED')),
    lock_version         bigint       not null default 0,
    created_at           timestamptz  not null default now(),
    updated_at           timestamptz  not null default now()
);

create index ix_recipe_status_created on kitchen.recipe (status, created_at desc);
create index ix_recipe_cuisine on kitchen.recipe (cuisine_id);
create index ix_recipe_category on kitchen.recipe (category_id);
create index ix_recipe_source on kitchen.recipe (source_id);
-- Kandydat na duplikat szuka się po podobieństwie tytułu (import „zastąp").
create index ix_recipe_title_trgm on kitchen.recipe using gin (title gin_trgm_ops);

create table kitchen.recipe_tag (
    recipe_id  bigint not null references kitchen.recipe(id) on delete cascade,
    tag_id     bigint not null references kitchen.tag(id),
    primary key (recipe_id, tag_id)
);

create table kitchen.recipe_diet (
    recipe_id  bigint not null references kitchen.recipe(id) on delete cascade,
    diet_id    bigint not null references kitchen.diet(id),
    primary key (recipe_id, diet_id)
);

-- Składnik przepisu. `display_name` to nazwa z tego przepisu („czerwona
-- cebula"), `ingredient_id` to pozycja katalogu — puste, dopóki nic nie
-- dopasowano. `quantity_min`/`quantity_max` są równe przy jednej wartości,
-- a puste przy „szczypcie" i „do smaku", które opisuje `quantity_text`.
create table kitchen.recipe_ingredient (
    id             bigserial     primary key,
    recipe_id      bigint        not null references kitchen.recipe(id) on delete cascade,
    position       integer       not null,
    group_label    varchar(100),
    ingredient_id  bigint        references kitchen.ingredient(id),
    display_name   varchar(200)  not null,
    source_text    text,
    quantity_min   numeric(10,3) check (quantity_min is null or quantity_min >= 0),
    quantity_max   numeric(10,3) check (quantity_max is null or quantity_max >= 0),
    unit_id        bigint        references kitchen.unit(id),
    quantity_text  varchar(100),
    preparation    varchar(200),
    optional       boolean       not null default false,
    note           text,
    constraint ck_recipe_ingredient_range check (
        quantity_min is null or quantity_max is null or quantity_min <= quantity_max)
);

create index ix_recipe_ingredient_recipe on kitchen.recipe_ingredient (recipe_id, position);
create index ix_recipe_ingredient_ingredient on kitchen.recipe_ingredient (ingredient_id);

create table kitchen.recipe_ingredient_alternative (
    id                    bigserial     primary key,
    recipe_ingredient_id  bigint        not null references kitchen.recipe_ingredient(id) on delete cascade,
    position              integer       not null,
    ingredient_id         bigint        references kitchen.ingredient(id),
    display_name          varchar(200)  not null,
    quantity_min          numeric(10,3),
    quantity_max          numeric(10,3),
    unit_id               bigint        references kitchen.unit(id),
    quantity_text         varchar(100),
    note                  text
);

create index ix_recipe_alternative_ingredient on kitchen.recipe_ingredient_alternative (recipe_ingredient_id, position);

create table kitchen.recipe_step (
    id                bigserial    primary key,
    recipe_id         bigint       not null references kitchen.recipe(id) on delete cascade,
    position          integer      not null,
    group_label       varchar(100),
    text              text         not null,
    source_text       text,
    duration_minutes  integer      check (duration_minutes is null or duration_minutes >= 0),
    temperature_c     integer      check (temperature_c is null or temperature_c between 0 and 400),
    temperature_note  varchar(100)
);

create index ix_recipe_step_recipe on kitchen.recipe_step (recipe_id, position);

-- Który składnik idzie w którym kroku. Bez tego skalowanie porcji i podświetlanie
-- „w tym kroku użyjesz" musiałyby parsować tekst kroku.
create table kitchen.recipe_step_ingredient (
    step_id               bigint not null references kitchen.recipe_step(id) on delete cascade,
    recipe_ingredient_id  bigint not null references kitchen.recipe_ingredient(id) on delete cascade,
    primary key (step_id, recipe_ingredient_id)
);

create table kitchen.recipe_step_equipment (
    step_id       bigint not null references kitchen.recipe_step(id) on delete cascade,
    equipment_id  bigint not null references kitchen.equipment(id),
    primary key (step_id, equipment_id)
);

-- Uwagi do całego przepisu, nie do konkretnej wersji: „za słone", „następnym
-- razem połowa cukru".
create table kitchen.recipe_note (
    id          bigserial   primary key,
    recipe_id   bigint      not null references kitchen.recipe(id) on delete cascade,
    body        text        not null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create index ix_recipe_note_recipe on kitchen.recipe_note (recipe_id, created_at desc);

-- ── Dziennik zmian ─────────────────────────────────────────────────────────

-- Jedna edycja = jedna rewizja. Rewizja 1 powstaje przy założeniu przepisu.
create table kitchen.recipe_revision (
    id                         bigserial   primary key,
    recipe_id                  bigint      not null references kitchen.recipe(id) on delete cascade,
    revision_no                integer     not null,
    origin                     varchar(20) not null
        check (origin in ('MANUAL','IMPORT','CHAT','RESTORE')),
    change_summary             varchar(500),
    restored_from_revision_no  integer,
    created_at                 timestamptz not null default now(),
    constraint ux_recipe_revision_no unique (recipe_id, revision_no)
);

create index ix_recipe_revision_recipe on kitchen.recipe_revision (recipe_id, revision_no desc);

-- Zmienione pola tej rewizji. `removed_row` niesie pełną treść usuniętego
-- wiersza — bez niej nie dałoby się go odtworzyć przy cofaniu zmian. Trzymamy
-- go jako tekst, a nie jsonb, bo nigdy nie zaglądamy do środka zapytaniem:
-- to ładunek do odczytania w całości, nie dane do przeszukiwania.
create table kitchen.recipe_change (
    id            bigserial    primary key,
    revision_id   bigint       not null references kitchen.recipe_revision(id) on delete cascade,
    target_type   varchar(30)  not null check (target_type in
        ('RECIPE','INGREDIENT','ALTERNATIVE','STEP','TAG','DIET','STEP_INGREDIENT','STEP_EQUIPMENT')),
    target_id     bigint,
    target_label  varchar(300),
    operation     varchar(10)  not null check (operation in ('ADD','UPDATE','REMOVE','MOVE')),
    field         varchar(50),
    old_value     text,
    new_value     text,
    removed_row   text,
    constraint ck_recipe_change_field check (
        (operation in ('UPDATE','MOVE') and field is not null)
        or (operation in ('ADD','REMOVE') and field is null))
);

create index ix_recipe_change_revision on kitchen.recipe_change (revision_id);
