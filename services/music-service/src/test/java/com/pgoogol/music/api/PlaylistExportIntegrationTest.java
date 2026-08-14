package com.pgoogol.music.api;

import com.pgoogol.music.TestcontainersConfiguration;
import com.pgoogol.music.catalog.TrackCatalog;
import com.pgoogol.music.catalog.TrackCatalogRepository;
import com.pgoogol.music.enrichment.spotify.SpotifyAccount;
import com.pgoogol.music.enrichment.spotify.SpotifyAccountRepository;
import com.pgoogol.music.enrichment.spotify.SpotifyPlaylistClient;
import com.pgoogol.music.library.LibraryEntryRepository;
import com.pgoogol.music.playlist.Playlist;
import com.pgoogol.music.playlist.PlaylistRepository;
import com.pgoogol.music.playlist.PlaylistTrack;
import com.pgoogol.music.playlist.PlaylistTrackRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Eksport setu na Spotify (DoD M2.4): pierwszy eksport zakłada playlistę,
 * kolejny nadpisuje tę samą, zachowując kolejność setu.
 */
// odświeżanie w tle (D35) wyłączone: test trzyma połączone konto, a zadanie
// cykliczne sięgnęłoby po playlisty w środku przebiegu
@SpringBootTest(properties = "ingestion.playlist-refresh.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlaylistExportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpotifyAccountRepository accountRepository;

    @Autowired
    private TrackCatalogRepository trackCatalogRepository;

    @Autowired
    private LibraryEntryRepository libraryEntryRepository;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private PlaylistTrackRepository playlistTrackRepository;

    @MockitoBean
    private SpotifyPlaylistClient playlistClient;

    private Long playlistId;

    @BeforeEach
    void seedSetAndAccount() {

        accountRepository.save(new SpotifyAccount("dj-pgoogol", "DJ pgoogol", "access", "refresh",
            Instant.now().plus(1, ChronoUnit.HOURS), "playlist-modify-private"));
        given(playlistClient.createPlaylist(anyString(), anyString(), anyString()))
            .willReturn("spotify-nowa");
        TrackCatalog first = trackCatalogRepository.save(
            new TrackCatalog("sp-peak", "Vivir Mi Vida", "Marc Anthony"));
        TrackCatalog second = trackCatalogRepository.save(
            new TrackCatalog("sp-warmup", "Bésame Mucho", "Consuelo Velázquez"));
        Playlist playlist = playlistRepository.save(new Playlist("Wesele Kowalskich"));
        playlistTrackRepository.saveAll(List.of(
            new PlaylistTrack(playlist, first, 0),
            new PlaylistTrack(playlist, second, 1)));
        playlistId = playlist.getId();
    }

    @AfterEach
    void cleanDatabase() {

        playlistTrackRepository.deleteAll();
        playlistRepository.deleteAll();
        libraryEntryRepository.deleteAll();
        trackCatalogRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void export_whenSetExportedFirstTime_createsPlaylistAndRemembersItsId() throws Exception {

        // when + then
        mockMvc.perform(post("/api/playlists/%d/export-to-spotify".formatted(playlistId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.spotifyPlaylistId").value("spotify-nowa"))
            .andExpect(jsonPath("$.exportedTracks").value(2))
            .andExpect(jsonPath("$.created").value(true))
            .andExpect(jsonPath("$.spotifyUrl")
                .value("https://open.spotify.com/playlist/spotify-nowa"));

        // kolejność setu trafia na Spotify jeden do jednego
        ArgumentCaptor<List<String>> uris = ArgumentCaptor.captor();
        then(playlistClient).should().replaceTracks(eq("spotify-nowa"), uris.capture());
        assertThat(uris.getValue()).containsExactly("sp-peak", "sp-warmup");
        assertThat(playlistRepository.findById(playlistId))
            .hasValueSatisfying(playlist ->
                assertThat(playlist.getSpotifyPlaylistId()).isEqualTo("spotify-nowa"));
    }

    @Test
    void export_whenSetAlreadyExported_overwritesSamePlaylist() throws Exception {

        // given
        mockMvc.perform(post("/api/playlists/%d/export-to-spotify".formatted(playlistId)))
            .andExpect(status().isOk());

        // when + then
        mockMvc.perform(post("/api/playlists/%d/export-to-spotify".formatted(playlistId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.spotifyPlaylistId").value("spotify-nowa"))
            .andExpect(jsonPath("$.created").value(false));

        then(playlistClient).should().createPlaylist(anyString(), anyString(), anyString());
    }

    @Test
    void export_whenSetEmpty_returns400AndDoesNotTouchSpotify() throws Exception {

        // given
        Playlist empty = playlistRepository.save(new Playlist("Pusty set"));

        // when + then
        mockMvc.perform(post("/api/playlists/%d/export-to-spotify".formatted(empty.getId())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("PLAYLIST_EMPTY"));
        then(playlistClient).should(never())
            .createPlaylist(anyString(), anyString(), anyString());
    }

    @Test
    void export_whenAccountNotConnected_returns400WithGuidance() throws Exception {

        // given
        accountRepository.deleteAll();

        // when + then
        mockMvc.perform(post("/api/playlists/%d/export-to-spotify".formatted(playlistId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("SPOTIFY_NOT_CONNECTED"));
    }

    @Test
    void export_whenPlaylistMissing_returns404() throws Exception {

        // when + then
        mockMvc.perform(post("/api/playlists/424242/export-to-spotify"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("PLAYLIST_NOT_FOUND"));
    }
}
