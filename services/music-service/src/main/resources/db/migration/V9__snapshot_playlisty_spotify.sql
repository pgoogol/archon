-- Snapshot playlisty ze Spotify. Pozwala pominąć pobranie utworów playlisty,
-- która nie zmieniła się od ostatniego importu: Spotify zwraca snapshot_id
-- w nagłówku playlisty, więc porównanie kosztuje zero dodatkowych wywołań.
-- Puste = playlista nigdy nie została zaimportowana w całości.
ALTER TABLE playlist ADD COLUMN IF NOT EXISTS spotify_snapshot_id varchar(128);
