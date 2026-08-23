-- Jedna instancja Postgresa, baza per serwis. Kolejny serwis w monorepo dokłada
-- tu swoją linijkę i nie wchodzi w cudzy schemat — bez tego pierwsza kolizja
-- nazw tabel wychodzi dopiero przy drugim serwisie.
--
-- Wszystkie bazy należą do domyślnej roli `postgres`: to środowisko wyłącznie
-- deweloperskie, a osobne role per serwis dawały tu tylko kolejne hasła do
-- wpisania w konfiguracji, nie izolację, której i tak nikt nie egzekwuje.
--
-- Skrypt wykonuje się TYLKO przy inicjalizacji pustego wolumenu, więc dopisanie
-- serwisu nie dotrze do środowiska postawionego wcześniej. NIE kasuj wtedy
-- wolumenu — dołóż bazę na działającym kontenerze:
--   docker compose -f deploy/compose/docker-compose.yml exec -T postgres \
--     psql -U postgres -c "create database finance"

select 'create database music'
 where not exists (select 1 from pg_database where datname = 'music')\gexec

select 'create database finance'
 where not exists (select 1 from pg_database where datname = 'finance')\gexec
