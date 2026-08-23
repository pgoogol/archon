package com.pgoogol.music.playlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    Optional<Playlist> findBySpotifyPlaylistId(String spotifyPlaylistId);

    /** Snapshoty znanych playlist Spotify — jednym zapytaniem na cały przebieg importu. */
    @Query("""
        select new com.pgoogol.music.playlist.PlaylistSnapshot(p.spotifyPlaylistId, p.spotifySnapshotId)
        from Playlist p
        where p.spotifyPlaylistId in :spotifyPlaylistIds
        """)
    List<PlaylistSnapshot> findSnapshots(Collection<String> spotifyPlaylistIds);

    /** Lista playlist z liczbą utworów — jednym zapytaniem, bez dociągania utworów. */
    @Query("""
        select new com.pgoogol.music.playlist.PlaylistSummary(
            p.id, p.name, p.spotifyPlaylistId, p.createdAt, count(pt.id), p.version)
        from Playlist p
        left join PlaylistTrack pt on pt.playlist = p
        group by p.id, p.name, p.spotifyPlaylistId, p.createdAt, p.version
        order by p.createdAt desc
        """)
    List<PlaylistSummary> findAllSummaries();
}
