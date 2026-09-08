-- V1: same rozszerzenia Postgresa, bez tabel.
--
-- Schemat przepisów wchodzi osobną migracją razem z encjami. Ta idzie pierwsza,
-- żeby świeża baza była kompletna od startu: wyszukiwarka stoi na `pg_trgm`
-- (literówki, „cukini" → „cukinia") i na `unaccent` (polskie znaki w zapytaniu),
-- a rozszerzenia trzeba założyć zanim powstaną indeksy, które ich używają.
--
-- Celowo w schemacie `public`, mimo że tabele serwisu mieszkają w `kitchen`:
-- funkcje rozszerzeń mają się rozwiązywać bez kwalifikowania nazwą schematu.

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;
