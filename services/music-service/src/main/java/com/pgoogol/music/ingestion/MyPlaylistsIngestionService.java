package com.pgoogol.music.ingestion;

import com.pgoogol.music.common.AppException;
import com.pgoogol.music.common.ErrorCodes;
import com.pgoogol.music.common.ExceptionMessageConstants;
import com.pgoogol.music.common.RateLimitedException;
import com.pgoogol.music.common.ValidationException;
import com.pgoogol.music.enrichment.spotify.SpotifyAccountService;
import com.pgoogol.music.enrichment.spotify.SpotifyPlaylist;
import com.pgoogol.music.enrichment.spotify.SpotifyPlaylistClient;
import com.pgoogol.music.library.LibrarySource;
import com.pgoogol.music.playlist.PlaylistRepository;
import com.pgoogol.music.playlist.PlaylistSnapshot;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Import wszystkich własnych playlist właściciela (M2.2, tryb C). Playlisty
 * obserwowane, ale cudze, są pomijane — te importuje się pojedynczo po linku
 * (tryb D). Każda playlista idzie w osobnej transakcji {@link PlaylistIngestionService},
 * a jej awaria trafia do raportu zamiast przerywać przebieg — powtórzenie
 * importu jest bezpieczne, więc nieudane playlisty wystarczy powtórzyć.
 *
 * <p><b>Przebieg jest przyrostowy.</b> Kwota Web API liczy się per konto
 * dewelopera, a pobranie utworów playlisty to osobne wywołanie na każde 100
 * pozycji — pobieranie za każdym razem wszystkiego wyczerpywało budżet dobowy.
 * Spotify podaje w nagłówku playlisty {@code snapshot_id}, więc przebieg
 * pobiera tylko te playlisty, które zmieniły się od ostatniego importu:
 * odświeżenie bez zmian kosztuje tyle, co jedna lista playlist.</p>
 */
@Service
@RequiredArgsConstructor
public class MyPlaylistsIngestionService {

    private static final Logger log = LoggerFactory.getLogger(MyPlaylistsIngestionService.class);

    private final SpotifyAccountService accountService;
    private final SpotifyPlaylistClient playlistClient;
    private final PlaylistIngestionService playlistIngestionService;
    private final PlaylistRepository playlistRepository;

    public MyPlaylistsIngestReport ingestMyPlaylists() {

        String ownerId = accountService.connectedUserId()
            .orElseThrow(() -> new ValidationException(ErrorCodes.SPOTIFY_NOT_CONNECTED,
                ExceptionMessageConstants.SPOTIFY_NOT_CONNECTED));
        List<SpotifyPlaylist> owned = ownedPlaylists(ownerId);
        Map<String, String> knownSnapshots = knownSnapshots(owned);
        log.info("Import własnych playlist konta {}: {} do sprawdzenia", ownerId, owned.size());

        IngestRun run = new IngestRun();
        owned.forEach(playlist -> ingestOne(playlist, knownSnapshots, run));

        MyPlaylistsIngestReport report = run.toReport();
        log.info("""
            Import własnych playlist konta {} zakończony: zaimportowane={}, bez zmian={}, \
            nieudane={}, nietknięte={}""",
            ownerId, report.imported().size(), report.unchanged().size(),
            report.failed().size(), report.notAttempted().size());
        return report;
    }

    private List<SpotifyPlaylist> ownedPlaylists(String ownerId) {

        List<SpotifyPlaylist> all = playlistClient.getMyPlaylists();
        return all.stream()
            .filter(playlist -> Objects.equals(playlist.ownerId(), ownerId))
            .toList();
    }

    /** Snapshoty z ostatnich udanych importów — jedno zapytanie na cały przebieg. */
    private Map<String, String> knownSnapshots(List<SpotifyPlaylist> owned) {

        if (owned.isEmpty()) {

            return Map.of();
        }
        Set<String> spotifyPlaylistIds = owned.stream()
            .map(SpotifyPlaylist::spotifyPlaylistId)
            .collect(Collectors.toSet());
        List<PlaylistSnapshot> snapshots = playlistRepository.findSnapshots(spotifyPlaylistIds);
        return snapshots.stream()
            .filter(snapshot -> Objects.nonNull(snapshot.snapshotId()))
            .collect(Collectors.toMap(PlaylistSnapshot::spotifyPlaylistId,
                PlaylistSnapshot::snapshotId, (first, second) -> first));
    }

    private void ingestOne(SpotifyPlaylist playlist, Map<String, String> knownSnapshots,
                           IngestRun run) {

        if (run.isStopped()) {

            run.notAttempted(playlist);
            return;
        }
        if (isUnchanged(playlist, knownSnapshots)) {

            run.unchanged(playlist);
            return;
        }
        importOne(playlist, run);
    }

    private boolean isUnchanged(SpotifyPlaylist playlist, Map<String, String> knownSnapshots) {

        if (Objects.isNull(playlist.snapshotId())) {

            return false;
        }
        String known = knownSnapshots.get(playlist.spotifyPlaylistId());
        return Objects.equals(known, playlist.snapshotId());
    }

    /**
     * Każda playlista idzie osobno i osobno może paść — wygasły token, utwór
     * bez odpowiednika, chwilowe 5xx ze Spotify. Awaria trafia do raportu
     * i przebieg leci dalej: import kilkudziesięciu playlist jest zbyt drogi,
     * żeby wywracać go na jednej.
     *
     * <p>Wyjątkiem jest wyczerpana kwota: wtedy każda kolejna playlista to
     * pewne odrzucenie, więc przebieg się zatrzymuje, a reszta trafia do raportu
     * jako nietknięta.</p>
     */
    private void importOne(SpotifyPlaylist playlist, IngestRun run) {

        try {

            PlaylistIngestReport report = playlistIngestionService.ingest(playlist,
                LibrarySource.PLAYLIST);
            run.imported(report);
        } catch (RateLimitedException ex) {

            log.warn("Import przerwany na playliście '{}' ({}): {} — powtórz, gdy kwota wróci",
                playlist.name(), playlist.spotifyPlaylistId(), ex.getMessage());
            run.stop();
            run.notAttempted(playlist);
        } catch (AppException ex) {

            log.warn("Playlista '{}' ({}) pominięta: {} — {}", playlist.name(),
                playlist.spotifyPlaylistId(), ex.getErrorCode(), ex.getMessage());
            run.failed(playlist, ex.getErrorCode(), ex.getMessage());
        } catch (RuntimeException ex) {

            log.error("Playlista '{}' ({}) pominięta — nieoczekiwany błąd",
                playlist.name(), playlist.spotifyPlaylistId(), ex);
            run.failed(playlist, ErrorCodes.INTERNAL_ERROR,
                ExceptionMessageConstants.PLAYLIST_IMPORT_FAILED);
        }
    }

    /** Stan jednego przebiegu — zbiera cztery listy raportu i decyzję o zatrzymaniu. */
    private static final class IngestRun {

        private final List<PlaylistIngestReport> imported = new ArrayList<>();
        private final List<FailedPlaylist> failed = new ArrayList<>();
        private final List<SkippedPlaylist> unchanged = new ArrayList<>();
        private final List<SkippedPlaylist> notAttempted = new ArrayList<>();
        private boolean stopped;

        private boolean isStopped() {

            return stopped;
        }

        private void stop() {

            this.stopped = true;
        }

        private void imported(PlaylistIngestReport report) {

            imported.add(report);
        }

        private void failed(SpotifyPlaylist playlist, String errorCode, String reason) {

            failed.add(new FailedPlaylist(playlist.spotifyPlaylistId(), playlist.name(),
                errorCode, reason));
        }

        private void unchanged(SpotifyPlaylist playlist) {

            unchanged.add(toSkipped(playlist));
        }

        private void notAttempted(SpotifyPlaylist playlist) {

            notAttempted.add(toSkipped(playlist));
        }

        private SkippedPlaylist toSkipped(SpotifyPlaylist playlist) {

            return new SkippedPlaylist(playlist.spotifyPlaylistId(), playlist.name());
        }

        private MyPlaylistsIngestReport toReport() {

            return new MyPlaylistsIngestReport(List.copyOf(imported), List.copyOf(failed),
                List.copyOf(unchanged), List.copyOf(notAttempted));
        }
    }
}
