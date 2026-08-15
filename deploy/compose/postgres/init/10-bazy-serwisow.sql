-- Jedna instancja Postgresa, baza per serwis. Kolejny serwis w monorepo dokłada
-- tu swoje dwie linijki i nie wchodzi w cudzy schemat — bez tego pierwsza
-- kolizja nazw tabel wychodzi dopiero przy drugim serwisie.
--
-- Skrypt wykonuje się TYLKO przy inicjalizacji pustego wolumenu. Po dodaniu
-- serwisu do listy trzeba przeładować środowisko z czyszczeniem danych:
--   docker compose -f deploy/compose/docker-compose.yml down -v && … up -d

create user music with password 'music';
create database music owner music;
