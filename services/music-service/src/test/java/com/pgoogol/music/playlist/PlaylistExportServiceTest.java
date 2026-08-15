package com.pgoogol.music.playlist;

import com.pgoogol.music.catalog.TrackCatalog;
import com.pgoogol.music.catalog.TrackCatalogFixtures;
import com.pgoogol.music.common.NotFoundException;
import com.pgoogol.music.common.ValidationException;
import com.pgoogol.music.enrichment.spotify.SpotifyAccountService;
import com.pgoogol.music.enrichment.spotify.SpotifyPlaylistClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Eksport setu na Spotify. Kluczowe rozróżnienie: pierwszy eksport zakłada
 * playlistę i zapamiętuje jej id, każdy kolejny nadpisuje tę samą — inaczej
 * po tygodniu konto DJ-a tonie w kopiach tego samego setu.
 */
@ExtendWith(MockitoExtension.class)
class PlaylistExportServiceTest {

    private static final Long PLAYLIST_ID = 42L;
    private static final String OWNER_ID = "dj-pgoogol";
    private static final String SPOTIFY_PLAYLIST_ID = "37i9dQZF1DXcBWIGoYBM5M";

    @Mock
    private PlaylistRepository playlistRepository;

    @Mock
    private PlaylistTrackRepository playlistTrackRepository;

    @Mock
    private SpotifyPlaylistClient playlistClient;

    @Mock
    private SpotifyAccountService accountService;

    @InjectMocks
    private PlaylistExportService service;

    @Captor
    private ArgumentCaptor<List<String>> trackIdsCaptor;

    @Test
    @DisplayName("pierwszy eksport zakłada playlistę i zapamiętuje jej identyfikator")
    void export_whenPlaylistNeverExported_createsPlaylistAndStoresItsId() {

        // given
        Playlist playlist = playlist(null);
        givenPlaylistWithTracks(playlist, "trk-1", "trk-2");
        given(accountService.connectedUserId()).willReturn(Optional.of(OWNER_ID));
        given(playlistClient.createPlaylist(eq(OWNER_ID), anyString(), anyString()))
            .willReturn(SPOTIFY_PLAYLIST_ID);

        // when
        PlaylistExport export = service.export(PLAYLIST_ID);

        // then
        assertThat(export.created()).isTrue();
        assertThat(export.spotifyPlaylistId()).isEqualTo(SPOTIFY_PLAYLIST_ID);
        assertThat(playlist.getSpotifyPlaylistId()).isEqualTo(SPOTIFY_PLAYLIST_ID);
        assertThat(export.spotifyUrl()).endsWith(SPOTIFY_PLAYLIST_ID);
    }

    @Test
    @DisplayName("kolejny eksport nadpisuje istniejącą playlistę zamiast zakładać nową")
    void export_whenPlaylistAlreadyExported_replacesTracksWithoutCreating() {

        // given
        Playlist playlist = playlist(SPOTIFY_PLAYLIST_ID);
        givenPlaylistWithTracks(playlist, "trk-1");
        given(accountService.connectedUserId()).willReturn(Optional.of(OWNER_ID));

        // when
        PlaylistExport export = service.export(PLAYLIST_ID);

        // then
        assertThat(export.created()).isFalse();
        verify(playlistClient, never()).createPlaylist(anyString(), anyString(), anyString());
        verify(playlistClient).replaceTracks(eq(SPOTIFY_PLAYLIST_ID), any());
    }

    @Test
    @DisplayName("utwory jadą na Spotify w kolejności setu")
    void export_sendsTracksInSetOrder() {

        // given
        givenPlaylistWithTracks(playlist(SPOTIFY_PLAYLIST_ID), "trk-1", "trk-2", "trk-3");
        given(accountService.connectedUserId()).willReturn(Optional.of(OWNER_ID));

        // when
        service.export(PLAYLIST_ID);

        // then
        verify(playlistClient).replaceTracks(eq(SPOTIFY_PLAYLIST_ID), trackIdsCaptor.capture());
        assertThat(trackIdsCaptor.getValue()).containsExactly("trk-1", "trk-2", "trk-3");
    }

    @Test
    @DisplayName("eksport pustego setu jest odrzucany, zanim ruszy cokolwiek na Spotify")
    void export_whenSetIsEmpty_throwsPlaylistEmpty() {

        // given
        givenPlaylistWithTracks(playlist(SPOTIFY_PLAYLIST_ID));

        // when
        Throwable thrown = catchThrowable(() -> service.export(PLAYLIST_ID));

        // then
        assertThat(thrown).isInstanceOf(ValidationException.class);
        assertThat(((ValidationException) thrown).getErrorCode()).isEqualTo("PLAYLIST_EMPTY");
        verify(playlistClient, never()).replaceTracks(anyString(), any());
    }

    @Test
    @DisplayName("brak podłączonego konta zatrzymuje eksport z czytelnym kodem")
    void export_whenSpotifyNotConnected_throwsSpotifyNotConnected() {

        // given
        givenPlaylistWithTracks(playlist(SPOTIFY_PLAYLIST_ID), "trk-1");
        given(accountService.connectedUserId()).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.export(PLAYLIST_ID));

        // then
        assertThat(thrown).isInstanceOf(ValidationException.class);
        assertThat(((ValidationException) thrown).getErrorCode()).isEqualTo("SPOTIFY_NOT_CONNECTED");
        verify(playlistClient, never()).replaceTracks(anyString(), any());
    }

    @Test
    @DisplayName("eksport nieistniejącego setu kończy się PLAYLIST_NOT_FOUND")
    void export_whenPlaylistMissing_throwsNotFound() {

        // given
        given(playlistRepository.findById(PLAYLIST_ID)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.export(PLAYLIST_ID));

        // then
        assertThat(thrown).isInstanceOf(NotFoundException.class);
        assertThat(((NotFoundException) thrown).getErrorCode()).isEqualTo("PLAYLIST_NOT_FOUND");
    }

    private Playlist playlist(String spotifyPlaylistId) {

        Playlist playlist = new Playlist("Wesele Kasi i Marka");
        playlist.setSpotifyPlaylistId(spotifyPlaylistId);
        return playlist;
    }

    private void givenPlaylistWithTracks(Playlist playlist, String... trackIds) {

        given(playlistRepository.findById(PLAYLIST_ID)).willReturn(Optional.of(playlist));
        List<PlaylistTrack> entries = IntStream.range(0, trackIds.length)
            .mapToObj(position -> playlistTrack(playlist, trackIds[position], position))
            .toList();
        given(playlistTrackRepository.findAllByPlaylistIdOrderByPositionAsc(PLAYLIST_ID))
            .willReturn(entries);
    }

    private PlaylistTrack playlistTrack(Playlist playlist, String spotifyId, int position) {

        TrackCatalog track = TrackCatalogFixtures.skeletonTrack(spotifyId);
        return new PlaylistTrack(playlist, track, position);
    }
}
