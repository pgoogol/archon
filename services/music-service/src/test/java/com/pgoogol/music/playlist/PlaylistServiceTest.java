package com.pgoogol.music.playlist;

import com.pgoogol.music.catalog.ManualMetricsRepository;
import com.pgoogol.music.catalog.TrackCatalog;
import com.pgoogol.music.catalog.TrackCatalogFixtures;
import com.pgoogol.music.catalog.TrackCatalogRepository;
import com.pgoogol.music.common.ConflictException;
import com.pgoogol.music.common.NotFoundException;
import com.pgoogol.music.common.ValidationException;
import com.pgoogol.music.library.LibraryEntryRepository;
import com.pgoogol.music.library.TrackSlotOverride;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Planowanie setów. Trzy rzeczy są tu nieoczywiste i dlatego badane wprost:
 * pozycje muszą zostać zwarte po usunięciu utworu, nowa kolejność musi być
 * permutacją składu (inaczej drag&drop po cichu gubi utwór), a zmiana wierszy
 * playlisty musi podbić wersję agregatu.
 */
@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    private static final Long PLAYLIST_ID = 42L;
    private static final String FIRST = "4uLU6hMCjMI75M1A2tKUQC";
    private static final String SECOND = "1nZzLMBiPPUlYAcZDkCsSN";
    private static final String THIRD = "6habFhsOp2NvshLv26DqMb";
    private static final int CURRENT_VERSION = 0;

    @Mock
    private PlaylistRepository playlistRepository;

    @Mock
    private PlaylistTrackRepository playlistTrackRepository;

    @Mock
    private TrackCatalogRepository trackCatalogRepository;

    @Mock
    private LibraryEntryRepository libraryEntryRepository;

    @Mock
    private ManualMetricsRepository manualMetricsRepository;

    @Mock
    private DjSlotCalculator djSlotCalculator;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private PlaylistService service;

    @Captor
    private ArgumentCaptor<PlaylistTrack> playlistTrackCaptor;

    @Test
    @DisplayName("pusta nazwa playlisty jest odrzucana")
    void create_whenNameIsBlank_throwsValidation() {

        // when
        Throwable thrown = catchThrowable(() -> service.create("   "));

        // then
        assertThat(thrown).isInstanceOf(ValidationException.class);
        assertThat(((ValidationException) thrown).getErrorCode()).isEqualTo("PLAYLIST_NAME_EMPTY");
        verify(playlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("nazwa playlisty traci otaczające spacje")
    void create_trimsName() {

        // given
        given(playlistRepository.save(any())).willAnswer(call -> call.getArgument(0));

        // when
        PlaylistSummary summary = service.create("  Wesele Kasi  ");

        // then
        assertThat(summary.name()).isEqualTo("Wesele Kasi");
        assertThat(summary.trackCount()).isZero();
    }

    @Test
    @DisplayName("dodanie utworu spoza katalogu kończy się TRACK_NOT_FOUND")
    void addTrack_whenTrackNotInCatalog_throwsNotFound() {

        // given
        givenPlaylist(playlist());
        given(trackCatalogRepository.findById(FIRST)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.addTrack(PLAYLIST_ID, FIRST));

        // then
        assertThat(thrown).isInstanceOf(NotFoundException.class);
        assertThat(((NotFoundException) thrown).getErrorCode()).isEqualTo("TRACK_NOT_FOUND");
    }

    @Test
    @DisplayName("ten sam utwór nie wchodzi na playlistę dwa razy")
    void addTrack_whenTrackAlreadyOnPlaylist_throwsConflict() {

        // given
        Playlist playlist = playlist();
        givenPlaylist(playlist);
        given(trackCatalogRepository.findById(FIRST))
            .willReturn(Optional.of(TrackCatalogFixtures.enrichedTrack(FIRST)));
        given(playlistTrackRepository.findByPlaylistIdAndTrackSpotifyId(PLAYLIST_ID, FIRST))
            .willReturn(Optional.of(new PlaylistTrack(playlist, track(FIRST), 0)));

        // when
        Throwable thrown = catchThrowable(() -> service.addTrack(PLAYLIST_ID, FIRST));

        // then
        assertThat(thrown).isInstanceOf(ConflictException.class);
        assertThat(((ConflictException) thrown).getErrorCode()).isEqualTo("PLAYLIST_TRACK_EXISTS");
        verify(playlistTrackRepository, never()).save(any());
    }

    @Test
    @DisplayName("nowy utwór ląduje na końcu setu")
    void addTrack_appendsTrackAtLastPosition() {

        // given — na playliście są już dwa utwory
        Playlist playlist = playlist();
        givenPlaylist(playlist);
        given(trackCatalogRepository.findById(THIRD))
            .willReturn(Optional.of(TrackCatalogFixtures.enrichedTrack(THIRD)));
        given(playlistTrackRepository.findByPlaylistIdAndTrackSpotifyId(PLAYLIST_ID, THIRD))
            .willReturn(Optional.empty());
        given(playlistTrackRepository.countByPlaylistId(PLAYLIST_ID)).willReturn(2L);
        givenEmptyPlan();

        // when
        service.addTrack(PLAYLIST_ID, THIRD);

        // then
        verify(playlistTrackRepository).save(playlistTrackCaptor.capture());
        assertThat(playlistTrackCaptor.getValue().getPosition()).isEqualTo(2);
    }

    @Test
    @DisplayName("dodanie utworu podbija wersję agregatu")
    void addTrack_bumpsAggregateVersion() {

        // given
        Playlist playlist = playlist();
        givenPlaylist(playlist);
        given(trackCatalogRepository.findById(FIRST))
            .willReturn(Optional.of(TrackCatalogFixtures.enrichedTrack(FIRST)));
        given(playlistTrackRepository.findByPlaylistIdAndTrackSpotifyId(PLAYLIST_ID, FIRST))
            .willReturn(Optional.empty());
        given(playlistTrackRepository.countByPlaylistId(PLAYLIST_ID)).willReturn(0L);
        givenEmptyPlan();

        // when
        service.addTrack(PLAYLIST_ID, FIRST);

        // then
        verify(entityManager).lock(playlist, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    @Test
    @DisplayName("usunięcie utworu ze środka zwiera pozycje pozostałych")
    void removeTrack_renumbersRemainingPositionsWithoutGaps() {

        // given — po usunięciu pozycji 1 zostają wiersze z pozycjami 0 i 2
        Playlist playlist = playlist();
        givenPlaylist(playlist);
        PlaylistTrack removed = new PlaylistTrack(playlist, track(SECOND), 1);
        PlaylistTrack head = new PlaylistTrack(playlist, track(FIRST), 0);
        PlaylistTrack tail = new PlaylistTrack(playlist, track(THIRD), 2);
        given(playlistTrackRepository.findByPlaylistIdAndTrackSpotifyId(PLAYLIST_ID, SECOND))
            .willReturn(Optional.of(removed));
        given(playlistTrackRepository.findAllByPlaylistIdOrderByPositionAsc(PLAYLIST_ID))
            .willReturn(List.of(head, tail));
        givenEmptyPlan();

        // when
        service.removeTrack(PLAYLIST_ID, SECOND);

        // then
        assertThat(head.getPosition()).isZero();
        assertThat(tail.getPosition()).isEqualTo(1);
        verify(playlistTrackRepository).delete(removed);
    }

    @Test
    @DisplayName("usunięcie utworu spoza setu kończy się PLAYLIST_TRACK_NOT_FOUND")
    void removeTrack_whenTrackNotOnPlaylist_throwsNotFound() {

        // given
        givenPlaylist(playlist());
        given(playlistTrackRepository.findByPlaylistIdAndTrackSpotifyId(PLAYLIST_ID, FIRST))
            .willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.removeTrack(PLAYLIST_ID, FIRST));

        // then
        assertThat(thrown).isInstanceOf(NotFoundException.class);
        assertThat(((NotFoundException) thrown).getErrorCode())
            .isEqualTo("PLAYLIST_TRACK_NOT_FOUND");
    }

    @Test
    @DisplayName("nowa kolejność zmienia pozycje zgodnie z przysłaną listą")
    void reorder_appliesRequestedOrder() {

        // given
        Playlist playlist = playlist();
        givenPlaylist(playlist);
        PlaylistTrack first = new PlaylistTrack(playlist, track(FIRST), 0);
        PlaylistTrack second = new PlaylistTrack(playlist, track(SECOND), 1);
        given(playlistTrackRepository.findAllByPlaylistIdOrderByPositionAsc(PLAYLIST_ID))
            .willReturn(List.of(first, second));
        givenEmptyPlan();

        // when
        service.reorder(PLAYLIST_ID, List.of(SECOND, FIRST), CURRENT_VERSION);

        // then
        assertThat(second.getPosition()).isZero();
        assertThat(first.getPosition()).isEqualTo(1);
    }

    @Test
    @DisplayName("kolejność z pominiętym utworem jest odrzucana, zamiast go skasować")
    void reorder_whenTrackIsMissingFromOrder_throwsMismatch() {

        // given — front przysłał tylko jeden z dwóch utworów
        Playlist playlist = playlist();
        givenPlaylist(playlist);
        given(playlistTrackRepository.findAllByPlaylistIdOrderByPositionAsc(PLAYLIST_ID))
            .willReturn(List.of(
                new PlaylistTrack(playlist, track(FIRST), 0),
                new PlaylistTrack(playlist, track(SECOND), 1)));

        // when
        Throwable thrown = catchThrowable(
            () -> service.reorder(PLAYLIST_ID, List.of(FIRST), CURRENT_VERSION));

        // then
        assertThat(thrown).isInstanceOf(ValidationException.class);
        assertThat(((ValidationException) thrown).getErrorCode())
            .isEqualTo("PLAYLIST_ORDER_MISMATCH");
    }

    @Test
    @DisplayName("kolejność z powtórzonym utworem jest odrzucana")
    void reorder_whenOrderRepeatsTrack_throwsMismatch() {

        // given
        Playlist playlist = playlist();
        givenPlaylist(playlist);
        given(playlistTrackRepository.findAllByPlaylistIdOrderByPositionAsc(PLAYLIST_ID))
            .willReturn(List.of(
                new PlaylistTrack(playlist, track(FIRST), 0),
                new PlaylistTrack(playlist, track(SECOND), 1)));

        // when
        Throwable thrown = catchThrowable(
            () -> service.reorder(PLAYLIST_ID, List.of(FIRST, FIRST), CURRENT_VERSION));

        // then
        assertThat(thrown).isInstanceOf(ValidationException.class);
        assertThat(((ValidationException) thrown).getErrorCode())
            .isEqualTo("PLAYLIST_ORDER_MISMATCH");
    }

    @Test
    @DisplayName("brak wersji w żądaniu traktujemy jak niezgodność, nie jak zgodę")
    void reorder_whenVersionIsMissing_throwsResourceModified() {

        // given
        givenPlaylist(playlist());

        // when
        Throwable thrown = catchThrowable(
            () -> service.reorder(PLAYLIST_ID, List.of(FIRST), null));

        // then
        assertThat(thrown).isInstanceOf(ConflictException.class);
        assertThat(((ConflictException) thrown).getErrorCode()).isEqualTo("RESOURCE_MODIFIED");
    }

    @Test
    @DisplayName("zmiana nazwy z nieświeżą wersją jest odrzucana")
    void rename_whenVersionIsStale_throwsResourceModified() {

        // given
        givenPlaylist(playlist());

        // when
        Throwable thrown = catchThrowable(() -> service.rename(PLAYLIST_ID, "Nowa", 7));

        // then
        assertThat(thrown).isInstanceOf(ConflictException.class);
        assertThat(((ConflictException) thrown).getErrorCode()).isEqualTo("RESOURCE_MODIFIED");
    }

    @Test
    @DisplayName("override slotu DJ-a wygrywa z wyliczeniem")
    void get_whenSlotOverrideExists_prefersItOverCalculatedSlot() {

        // given
        Playlist playlist = playlist();
        givenPlaylist(playlist);
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack(FIRST);
        given(playlistTrackRepository.findAllWithTrackByPlaylistId(PLAYLIST_ID))
            .willReturn(List.of(new PlaylistTrack(playlist, track, 0)));
        given(libraryEntryRepository.findSlotOverrides(anySet()))
            .willReturn(List.of(new TrackSlotOverride(FIRST, DjSlot.PEAK.name())));
        given(manualMetricsRepository.findBySpotifyIdIn(anySet())).willReturn(List.of());

        // when
        PlaylistPlan plan = service.get(PLAYLIST_ID);

        // then
        assertThat(plan.tracks().get(0).djSlot()).isEqualTo(DjSlot.PEAK);
        assertThat(plan.tracks().get(0).djSlotOverride()).isEqualTo(DjSlot.PEAK.name());
        verify(djSlotCalculator, never()).calculate(any());
    }

    @Test
    @DisplayName("bez override slot liczy kalkulator")
    void get_whenNoOverride_usesCalculatedSlot() {

        // given
        Playlist playlist = playlist();
        givenPlaylist(playlist);
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack(FIRST);
        given(playlistTrackRepository.findAllWithTrackByPlaylistId(PLAYLIST_ID))
            .willReturn(List.of(new PlaylistTrack(playlist, track, 0)));
        given(libraryEntryRepository.findSlotOverrides(anySet())).willReturn(List.of());
        given(manualMetricsRepository.findBySpotifyIdIn(anySet())).willReturn(List.of());
        given(djSlotCalculator.calculate(track)).willReturn(Optional.of(DjSlot.WARMUP));

        // when
        PlaylistPlan plan = service.get(PLAYLIST_ID);

        // then
        assertThat(plan.tracks().get(0).djSlot()).isEqualTo(DjSlot.WARMUP);
    }

    @Test
    @DisplayName("pusty set nie odpytuje bazy o override'y ani metryki")
    void get_whenPlaylistIsEmpty_skipsSideLookups() {

        // given
        givenPlaylist(playlist());
        given(playlistTrackRepository.findAllWithTrackByPlaylistId(PLAYLIST_ID))
            .willReturn(List.of());

        // when
        PlaylistPlan plan = service.get(PLAYLIST_ID);

        // then
        assertThat(plan.tracks()).isEmpty();
        verify(libraryEntryRepository, never()).findSlotOverrides(anySet());
        verify(manualMetricsRepository, never()).findBySpotifyIdIn(anySet());
    }

    @Test
    @DisplayName("operacja na nieistniejącej playliście kończy się PLAYLIST_NOT_FOUND")
    void get_whenPlaylistMissing_throwsNotFound() {

        // given
        given(playlistRepository.findById(PLAYLIST_ID)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.get(PLAYLIST_ID));

        // then
        assertThat(thrown).isInstanceOf(NotFoundException.class);
        assertThat(((NotFoundException) thrown).getErrorCode()).isEqualTo("PLAYLIST_NOT_FOUND");
    }

    @Test
    @DisplayName("usunięcie playlisty nie rusza katalogu ani biblioteki")
    void delete_removesPlaylistOnly() {

        // given
        Playlist playlist = playlist();
        givenPlaylist(playlist);

        // when
        service.delete(PLAYLIST_ID);

        // then
        verify(playlistRepository).delete(playlist);
        verify(trackCatalogRepository, never()).delete(any());
        verify(libraryEntryRepository, never()).delete(any());
    }

    /**
     * Encja nieutrwalona ma {@code id == null}, a plan setu odpytuje bazę
     * właśnie po {@code playlist.getId()} — bez nadania id testy mierzyłyby
     * zapytanie o null zamiast o playlistę.
     */
    private Playlist playlist() {

        Playlist playlist = new Playlist("Wesele Kasi i Marka");
        ReflectionTestUtils.setField(playlist, "id", PLAYLIST_ID);
        return playlist;
    }

    private TrackCatalog track(String spotifyId) {

        return TrackCatalogFixtures.skeletonTrack(spotifyId);
    }

    private void givenPlaylist(Playlist playlist) {

        given(playlistRepository.findById(PLAYLIST_ID)).willReturn(Optional.of(playlist));
    }

    /** Plan po zmianie składu — treść nieistotna dla testów samej mutacji. */
    private void givenEmptyPlan() {

        given(playlistTrackRepository.findAllWithTrackByPlaylistId(PLAYLIST_ID))
            .willReturn(List.of());
    }
}
