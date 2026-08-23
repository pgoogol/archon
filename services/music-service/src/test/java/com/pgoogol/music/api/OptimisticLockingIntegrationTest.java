package com.pgoogol.music.api;

import tools.jackson.databind.ObjectMapper;
import com.pgoogol.music.TestcontainersConfiguration;
import com.pgoogol.music.catalog.TrackCatalog;
import com.pgoogol.music.catalog.TrackCatalogRepository;
import com.pgoogol.music.library.LibraryEntry;
import com.pgoogol.music.library.LibraryEntryRepository;
import com.pgoogol.music.library.LibrarySource;
import com.pgoogol.music.playlist.PlaylistRepository;
import com.pgoogol.music.playlist.PlaylistTrackRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Blokada optymistyczna na danych DJ-a (M5.2): dwie karty przeglądarki
 * nie mogą po cichu nadpisać sobie notatek ani kolejności setu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class OptimisticLockingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TrackCatalogRepository trackCatalogRepository;

    @Autowired
    private LibraryEntryRepository libraryEntryRepository;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private PlaylistTrackRepository playlistTrackRepository;

    @BeforeEach
    void seed() {

        trackCatalogRepository.save(new TrackCatalog("sp-vivir", "Vivir Mi Vida", "Marc Anthony"));
        trackCatalogRepository.save(new TrackCatalog("sp-carnaval", "Carnaval", "Celia Cruz"));
        libraryEntryRepository.save(new LibraryEntry(
            trackCatalogRepository.findById("sp-vivir").orElseThrow(), LibrarySource.PLAYLIST));
    }

    @AfterEach
    void cleanDatabase() {

        playlistTrackRepository.deleteAll();
        playlistRepository.deleteAll();
        libraryEntryRepository.deleteAll();
        trackCatalogRepository.deleteAll();
    }

    @Test
    @DisplayName("wpis biblioteki oddaje swoją wersję, a zapis ją podbija")
    void libraryEntry_exposesAndAdvancesVersion() throws Exception {

        mockMvc.perform(get("/music/api/v1/library/tracks/sp-vivir"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(0));

        mockMvc.perform(patchNotes("pierwsza notatka", 0))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("druga karta z nieaktualną wersją dostaje 409 i nie kasuje cudzej notatki")
    void libraryEntry_whenStaleVersion_returns409AndKeepsFirstWrite() throws Exception {

        // obie karty widzą wersję 0
        mockMvc.perform(patchNotes("notatka z pierwszej karty", 0))
            .andExpect(status().isOk());

        mockMvc.perform(patchNotes("notatka z drugiej karty", 0))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_MODIFIED"));

        mockMvc.perform(get("/music/api/v1/library/tracks/sp-vivir"))
            .andExpect(jsonPath("$.djNotes").value("notatka z pierwszej karty"));
    }

    @Test
    @DisplayName("PATCH bez wersji nie przechodzi — kontrakt jej wymaga")
    void libraryEntry_whenVersionMissing_returns400() throws Exception {

        mockMvc.perform(patch("/music/api/v1/library/tracks/sp-vivir")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"djNotes\": \"bez wersji\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("zmiana składu setu podbija wersję playlisty, choć zmienia wiersze utworów")
    void playlist_versionFollowsTrackChanges() throws Exception {

        long playlistId = createPlaylist();

        assertThat(playlistVersion(playlistId)).isZero();

        addTrack(playlistId, "sp-vivir");
        assertThat(playlistVersion(playlistId)).isEqualTo(1);

        addTrack(playlistId, "sp-carnaval");
        assertThat(playlistVersion(playlistId)).isEqualTo(2);
    }

    @Test
    @DisplayName("reorder z nieaktualną wersją dostaje 409 i zostawia kolejność w spokoju")
    void playlist_whenStaleVersionOnReorder_returns409() throws Exception {

        long playlistId = createPlaylist();
        addTrack(playlistId, "sp-vivir");
        addTrack(playlistId, "sp-carnaval");
        int stale = playlistVersion(playlistId);

        // pierwsza karta odwraca kolejność
        mockMvc.perform(reorder(playlistId, "sp-carnaval", "sp-vivir", stale))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tracks[0].track.spotifyId").value("sp-carnaval"));

        // druga karta wysyła kolejność sprzed tej zmiany
        mockMvc.perform(reorder(playlistId, "sp-vivir", "sp-carnaval", stale))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_MODIFIED"));

        mockMvc.perform(get("/music/api/v1/playlists/" + playlistId))
            .andExpect(jsonPath("$.tracks[0].track.spotifyId").value("sp-carnaval"));
    }

    @Test
    @DisplayName("zmiana nazwy setu też sprawdza wersję")
    void playlist_whenStaleVersionOnRename_returns409() throws Exception {

        long playlistId = createPlaylist();

        mockMvc.perform(patch("/music/api/v1/playlists/" + playlistId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Wesele\", \"version\": 0}"))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/music/api/v1/playlists/" + playlistId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Osiemnastka\", \"version\": 0}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_MODIFIED"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder patchNotes(
            String notes, int version) {

        return patch("/music/api/v1/library/tracks/sp-vivir")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"djNotes\": \"%s\", \"version\": %d}".formatted(notes, version));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder reorder(
            long playlistId, String first, String second, int version) {

        return put("/music/api/v1/playlists/%d/tracks".formatted(playlistId))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"spotifyIds\": [\"%s\", \"%s\"], \"version\": %d}"
                .formatted(first, second, version));
    }

    private long createPlaylist() throws Exception {

        String body = mockMvc.perform(post("/music/api/v1/playlists")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Set\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void addTrack(long playlistId, String spotifyId) throws Exception {

        mockMvc.perform(post("/music/api/v1/playlists/%d/tracks".formatted(playlistId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"spotifyId\": \"%s\"}".formatted(spotifyId)))
            .andExpect(status().isOk());
    }

    private int playlistVersion(long playlistId) throws Exception {

        String body = mockMvc.perform(get("/music/api/v1/playlists/" + playlistId))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("version").asInt();
    }
}
