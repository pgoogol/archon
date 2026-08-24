-- Playlisty zaimportowane bez ani jednego utworu mają zapisany snapshot, więc
-- kolejne przebiegi uznają je za aktualne i nigdy po nie nie sięgną. Czyszczenie
-- snapshotu tam, gdzie lokalnie nie ma żadnego utworu, przywraca im ścieżkę
-- ponownego pobrania. Playlista pusta naprawdę kosztuje przy tym tylko sprawdzenie
-- nagłówka i zapisze snapshot z powrotem przy najbliższym imporcie.
UPDATE playlist
SET spotify_snapshot_id = NULL
WHERE spotify_snapshot_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM playlist_track pt
      WHERE pt.playlist_id = playlist.id
  );
