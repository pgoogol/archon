-- Jedna instancja Postgresa, baza per serwis. Kolejny serwis w monorepo dokłada
-- tu swoje dwie linijki i nie wchodzi w cudzy schemat — bez tego pierwsza
-- kolizja nazw tabel wychodzi dopiero przy drugim serwisie.
--
-- Skrypt wykonuje się TYLKO przy inicjalizacji pustego wolumenu, więc dopisanie
-- serwisu nie dotrze do środowiska postawionego wcześniej. NIE kasuj wtedy
-- wolumenu — dołóż bazę na działającym kontenerze:
--   docker compose -f deploy/compose/docker-compose.yml exec -T postgres \
--     psql -U postgres -c "create user finance with password 'finance'" \
--                      -c "create database finance owner finance"

-- Zapis idempotentny: skrypt nie wywala startu kontenera, gdy rola albo baza
-- już istnieje. Ma to znaczenie przy dokładaniu kolejnego serwisu na środowisku,
-- którego nie chcemy kasować.
select 'create user music with password ''music'''
 where not exists (select 1 from pg_roles where rolname = 'music')\gexec
select 'create database music owner music'
 where not exists (select 1 from pg_database where datname = 'music')\gexec

select 'create user finance with password ''finance'''
 where not exists (select 1 from pg_roles where rolname = 'finance')\gexec
select 'create database finance owner finance'
 where not exists (select 1 from pg_database where datname = 'finance')\gexec
