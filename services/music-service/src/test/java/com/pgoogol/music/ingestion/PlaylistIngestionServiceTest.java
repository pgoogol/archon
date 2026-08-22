package com.pgoogol.music.ingestion;

import com.pgoogol.music.catalog.TrackCatalog;
import com.pgoogol.music.catalog.TrackCatalogRepository;
import com.pgoogol.music.enrichment.spotify.SpotifyAccountService;
import com.pgoogol.music.enrichment.spotify.SpotifyPlaylist;
import com.pgoogol.music.enrichment.spotify.SpotifyPlaylistClient;
import com.pgoogol.music.enrichment.spotify.SpotifyPlaylistItem;
import com.pgoogol.music.enrichment.spotify.SpotifyTrackMetadata;
import com.pgoogol.music.library.LibraryEntry;
import com.pgoogol.music.library.LibraryEntryRepository;
import com.pgoogol.music.library.LibrarySource;
import com.pgoogol.music.playlist.Playlist;
import com.pgoogol.music.playlist.PlaylistRepository;
import com.pgoogol.music.playlist.PlaylistTrack;
import com.pgoogol.music.playlist.PlaylistTrackRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Import playlisty ze Spotify. Trzy zachowania decydują o poprawności: playlista
 * połączonego konta jest własna, a cudza obca; metadane istniejącego utworu
 * uzupełniamy tylko tam, gdzie są puste (wzbogacanie jest właścicielem tego,
 * co już ustalone); a ponowny import odtwarza kolejność zamiast dokładać duplikaty.
 */
@ExtendWith(MockitoExtension.class)
class PlaylistIngestionServiceTest {

    private static final String PLAYLIST_ID = "37i9dQZF1DXcBWIGoYBM5M";
    private static final String OWNER_ID = "dj-pgoogol";
    private static final String FIRST = "4uLU6hMCjMI75M1A2tKUQC";
    private static final String SECOND = "1nZzLMBiPPUlYAcZDkCsSN";

    @Mock
    private SpotifyPlaylistUrlParser urlParser;

    @Mock
    private SpotifyPlaylistClient playlistClient;

    @Mock
    private SpotifyAccountService accountService;

    @Mock
    private TrackCatalogRepository trackCatalogRepository;

    @Mock
    private LibraryEntryRepository libraryEntryRepository;

    @Mock
    private PlaylistRepository playlistRepository;

    @Mock
    private PlaylistTrackRepository playlistTrackRepository;

    @InjectMocks
    private PlaylistIngestionService service;

    @Captor
    private ArgumentCaptor<List<LibraryEntry>> entriesCaptor;

    @Captor
    private ArgumentCaptor<List<PlaylistTrack>> playlistTracksCaptor;

    @Test
    @DisplayName("playlista połączonego konta wchodzi do biblioteki jako własna")
    void ingest_whenPlaylistBelongsToConnectedAccount_marksEntriesAsOwnPlaylist() {

        // given
        givenPlaylistWithItems(OWNER_ID, trackItem(0, FIRST));
        given(accountService.connectedUserId()).willReturn(Optional.of(OWNER_ID));
        givenEmptyCatalogAndLibrary();

        // when
        service.ingest("https://open.spotify.com/playlist/" + PLAYLIST_ID);

        // then
        verify(libraryEntryRepository).saveAll(entriesCaptor.capture());
        assertThat(entriesCaptor.getValue().get(0).getSource())
            .isEqualTo(LibrarySource.PLAYLIST);
    }

    @Test
    @DisplayName("cudza playlista wchodzi do biblioteki jako obca")
    void ingest_whenPlaylistBelongsToSomeoneElse_marksEntriesAsForeign() {

        // given
        givenPlaylistWithItems("ktos-inny", trackItem(0, FIRST));
        given(accountService.connectedUserId()).willReturn(Optional.of(OWNER_ID));
        givenEmptyCatalogAndLibrary();

        // when
        service.ingest(PLAYLIST_ID);

        // then
        verify(libraryEntryRepository).saveAll(entriesCaptor.capture());
        assertThat(entriesCaptor.getValue().get(0).getSource())
            .isEqualTo(LibrarySource.FOREIGN_PLAYLIST);
    }

    @Test
    @DisplayName("bez połączonego konta każda playlista jest obca")
    void ingest_whenNoAccountConnected_marksEntriesAsForeign() {

        // given
        givenPlaylistWithItems(OWNER_ID, trackItem(0, FIRST));
        given(accountService.connectedUserId()).willReturn(Optional.empty());
        givenEmptyCatalogAndLibrary();

        // when
        service.ingest(PLAYLIST_ID);

        // then
        verify(libraryEntryRepository).saveAll(entriesCaptor.capture());
        assertThat(entriesCaptor.getValue().get(0).getSource())
            .isEqualTo(LibrarySource.FOREIGN_PLAYLIST);
    }

    @Test
    @DisplayName("niedostępne pozycje trafiają do raportu jako pominięte")
    void ingest_whenItemUnavailable_reportsItAsSkipped() {

        // given
        givenPlaylistWithItems(OWNER_ID,
            trackItem(0, FIRST),
            new SpotifyPlaylistItem.Unavailable(1, "utwór niedostępny w regionie"));
        givenEmptyCatalogAndLibrary();

        // when
        PlaylistIngestReport report = service.ingest(playlist(OWNER_ID), LibrarySource.PLAYLIST);

        // then
        assertThat(report.skipped()).hasSize(1);
        assertThat(report.skipped().get(0).position()).isEqualTo(1);
        assertThat(report.tracks()).isEqualTo(1);
    }

    @Test
    @DisplayName("ten sam utwór dwa razy na playliście liczy się raz")
    void ingest_whenPlaylistRepeatsTrack_countsItOnce() {

        // given
        givenPlaylistWithItems(OWNER_ID, trackItem(0, FIRST), trackItem(1, FIRST));
        givenEmptyCatalogAndLibrary();

        // when
        PlaylistIngestReport report = service.ingest(playlist(OWNER_ID), LibrarySource.PLAYLIST);

        // then
        assertThat(report.tracks()).isEqualTo(1);
        assertThat(report.imported()).isEqualTo(1);
    }

    @Test
    @DisplayName("utwór znany bibliotece liczy się jako już istniejący")
    void ingest_whenTrackAlreadyInLibrary_countsItAsAlreadyExisted() {

        // given
        givenPlaylistWithItems(OWNER_ID, trackItem(0, FIRST), trackItem(1, SECOND));
        given(trackCatalogRepository.findExistingIds(anySet())).willReturn(Set.of());
        given(trackCatalogRepository.findAllById(anySet())).willReturn(List.of());
        given(libraryEntryRepository.findExistingTrackIds(anySet())).willReturn(Set.of(FIRST));
        givenCatalogReferencesAndPlaylist();

        // when
        PlaylistIngestReport report = service.ingest(playlist(OWNER_ID), LibrarySource.PLAYLIST);

        // then
        assertThat(report.imported()).isEqualTo(1);
        assertThat(report.alreadyExisted()).isEqualTo(1);
    }

    @Test
    @DisplayName("istniejącemu utworowi uzupełniamy tylko puste pola")
    void ingest_whenTrackInCatalog_fillsOnlyMissingMetadata() {

        // given — katalog ma już rok ustalony przez wzbogacanie; import go nie nadpisuje
        TrackCatalog existing = new TrackCatalog(FIRST, "Stary tytuł", "Stary artysta");
        existing.setYear(1999);
        givenPlaylistWithItems(OWNER_ID, trackItem(0, FIRST));
        given(trackCatalogRepository.findExistingIds(anySet())).willReturn(Set.of(FIRST));
        given(trackCatalogRepository.findAllById(Set.of(FIRST))).willReturn(List.of(existing));
        given(libraryEntryRepository.findExistingTrackIds(anySet())).willReturn(Set.of(FIRST));
        givenCatalogReferencesAndPlaylist();

        // when
        service.ingest(playlist(OWNER_ID), LibrarySource.PLAYLIST);

        // then
        assertThat(existing.getYear()).isEqualTo(1999);
        assertThat(existing.getTitle()).isEqualTo("Stary tytuł");
        assertThat(existing.getIsrc()).isEqualTo("USSD11300483");
    }

    @Test
    @DisplayName("nowy utwór dostaje komplet metadanych z playlisty")
    void ingest_whenTrackIsNew_appliesFullMetadata() {

        // given
        givenPlaylistWithItems(OWNER_ID, trackItem(0, FIRST));
        givenEmptyCatalogAndLibrary();
        ArgumentCaptor<List<TrackCatalog>> created = ArgumentCaptor.captor();

        // when
        service.ingest(playlist(OWNER_ID), LibrarySource.PLAYLIST);

        // then
        verify(trackCatalogRepository).saveAll(created.capture());
        TrackCatalog track = created.getValue().get(0);
        assertThat(track.getYear()).isEqualTo(2013);
        assertThat(track.getIsrc()).isEqualTo("USSD11300483");
        assertThat(track.getDurationMs()).isEqualTo(252306);
    }

    @Test
    @DisplayName("ponowny import odtwarza kolejność od zera zamiast dokładać wpisy")
    void ingest_replacesPlaylistTracksInSpotifyOrder() {

        // given
        givenPlaylistWithItems(OWNER_ID, trackItem(0, FIRST), trackItem(1, SECOND));
        givenEmptyCatalogAndLibrary();

        // when
        service.ingest(playlist(OWNER_ID), LibrarySource.PLAYLIST);

        // then
        verify(playlistTrackRepository).deleteByPlaylistId(any());
        verify(playlistTrackRepository).saveAll(playlistTracksCaptor.capture());
        assertThat(playlistTracksCaptor.getValue())
            .extracting(PlaylistTrack::getPosition)
            .containsExactly(0, 1);
    }

    @Test
    @DisplayName("ponowny import tej samej playlisty aktualizuje nazwę, nie tworzy drugiej")
    void ingest_whenPlaylistAlreadyImported_updatesNameInPlace() {

        // given
        Playlist existing = playlistEntity("Stara nazwa");
        givenPlaylistWithItems(OWNER_ID, trackItem(0, FIRST));
        givenEmptyCatalogAndLibraryWithoutPlaylist();
        given(playlistRepository.findBySpotifyPlaylistId(PLAYLIST_ID))
            .willReturn(Optional.of(existing));
        given(playlistRepository.save(any())).willAnswer(call -> call.getArgument(0));

        // when
        service.ingest(playlist(OWNER_ID), LibrarySource.PLAYLIST);

        // then
        assertThat(existing.getName()).isEqualTo("Wesele 2026");
        verify(playlistRepository).save(existing);
    }

    private SpotifyPlaylist playlist(String ownerId) {

        return new SpotifyPlaylist(PLAYLIST_ID, "Wesele 2026", ownerId, "DJ pgoogol", 1);
    }

    private Playlist playlistEntity(String name) {

        Playlist playlist = new Playlist(name);
        playlist.setSpotifyPlaylistId(PLAYLIST_ID);
        ReflectionTestUtils.setField(playlist, "id", 1L);
        return playlist;
    }

    private SpotifyPlaylistItem trackItem(int position, String spotifyId) {

        return new SpotifyPlaylistItem.Track(position, new SpotifyTrackMetadata(
            spotifyId, "Vivir Mi Vida", "Marc Anthony", "3.0", 2013, 252306, 80, false,
            "https://i.scdn.co/image/example", "USSD11300483"));
    }

    private void givenPlaylistWithItems(String ownerId, SpotifyPlaylistItem... items) {

        given(playlistClient.getPlaylistItems(PLAYLIST_ID)).willReturn(List.of(items));
        lenient()
            .when(urlParser.parsePlaylistId(any())).thenReturn(PLAYLIST_ID);
        lenient()
            .when(playlistClient.getPlaylist(PLAYLIST_ID)).thenReturn(playlist(ownerId));
    }

    private void givenEmptyCatalogAndLibrary() {

        given(trackCatalogRepository.findExistingIds(anySet())).willReturn(Set.of());
        given(trackCatalogRepository.findAllById(anySet())).willReturn(List.of());
        given(libraryEntryRepository.findExistingTrackIds(anySet())).willReturn(Set.of());
        givenCatalogReferencesAndPlaylist();
    }

    private void givenEmptyCatalogAndLibraryWithoutPlaylist() {

        given(trackCatalogRepository.findExistingIds(anySet())).willReturn(Set.of());
        given(trackCatalogRepository.findAllById(anySet())).willReturn(List.of());
        given(libraryEntryRepository.findExistingTrackIds(anySet())).willReturn(Set.of());
        given(trackCatalogRepository.getReferenceById(any()))
            .willAnswer(call -> new TrackCatalog(call.getArgument(0), "t", "a"));
    }

    private void givenCatalogReferencesAndPlaylist() {

        given(trackCatalogRepository.getReferenceById(any()))
            .willAnswer(call -> new TrackCatalog(call.getArgument(0), "t", "a"));
        given(playlistRepository.findBySpotifyPlaylistId(PLAYLIST_ID)).willReturn(Optional.empty());
        given(playlistRepository.save(any())).willAnswer(call -> {

            Playlist saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
    }
}
