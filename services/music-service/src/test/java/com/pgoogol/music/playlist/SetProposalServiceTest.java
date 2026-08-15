package com.pgoogol.music.playlist;

import com.pgoogol.music.catalog.CatalogSearchCriteria;
import com.pgoogol.music.catalog.CatalogService;
import com.pgoogol.music.catalog.TrackCatalog;
import com.pgoogol.music.catalog.TrackCatalogFixtures;
import com.pgoogol.music.common.ValidationException;
import com.pgoogol.music.library.LibraryEntryRepository;
import com.pgoogol.music.library.TrackDjData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Budowanie puli kandydatów pod generator setu. Serwis nic nie zapisuje —
 * bada się więc to, co decyduje o wyniku: walidacja długości i pozycji,
 * pierwszeństwo override'u slotu nad wyliczeniem oraz to, że pusta pula
 * kończy się czytelnym błędem zamiast pustym setem.
 */
@ExtendWith(MockitoExtension.class)
class SetProposalServiceTest {

    private static final Long PLAYLIST_ID = 42L;
    private static final String SPOTIFY_ID = "4uLU6hMCjMI75M1A2tKUQC";
    private static final int TARGET_MINUTES = 240;

    @Mock
    private CatalogService catalogService;

    @Mock
    private LibraryEntryRepository libraryEntryRepository;

    @Mock
    private DjSlotCalculator djSlotCalculator;

    @Mock
    private SetGenerator setGenerator;

    @Mock
    private SetSuggester setSuggester;

    @Mock
    private SetRules setRules;

    @Mock
    private PlaylistService playlistService;

    @InjectMocks
    private SetProposalService service;

    @Captor
    private ArgumentCaptor<List<SetCandidate>> candidatesCaptor;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @Test
    @DisplayName("set krótszy niż kwadrans jest odrzucany")
    void propose_whenTargetBelowMinimum_throwsOutOfRange() {

        // when
        Throwable thrown = catchThrowable(() -> service.propose(
            CatalogSearchCriteria.none(), SetProposalService.MIN_TARGET_MINUTES - 1,
            SetCurve.STANDARD, null));

        // then
        assertThat(thrown).isInstanceOf(ValidationException.class);
        assertThat(((ValidationException) thrown).getErrorCode())
            .isEqualTo("SET_TARGET_OUT_OF_RANGE");
        verify(catalogService, never()).search(any(), any(), any());
    }

    @Test
    @DisplayName("set dłuższy niż dwanaście godzin jest odrzucany")
    void propose_whenTargetAboveMaximum_throwsOutOfRange() {

        // when
        Throwable thrown = catchThrowable(() -> service.propose(
            CatalogSearchCriteria.none(), SetProposalService.MAX_TARGET_MINUTES + 1,
            SetCurve.STANDARD, null));

        // then
        assertThat(thrown).isInstanceOf(ValidationException.class);
        assertThat(((ValidationException) thrown).getErrorCode())
            .isEqualTo("SET_TARGET_OUT_OF_RANGE");
    }

    @Test
    @DisplayName("pusta pula kandydatów kończy się SET_NO_CANDIDATES, nie pustym setem")
    void propose_whenNoTrackPassesFilters_throwsNoCandidates() {

        // given
        givenCandidates();

        // when
        Throwable thrown = catchThrowable(() -> service.propose(
            CatalogSearchCriteria.none(), TARGET_MINUTES, SetCurve.STANDARD, null));

        // then
        assertThat(thrown).isInstanceOf(ValidationException.class);
        assertThat(((ValidationException) thrown).getErrorCode()).isEqualTo("SET_NO_CANDIDATES");
        verify(setGenerator, never()).generate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("pula kandydatów jest ucinana do sufitu 2000 utworów")
    void propose_capsCandidatePoolAtCeiling() {

        // given
        givenCandidates(TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID));
        given(libraryEntryRepository.findDjData(anyList())).willReturn(List.of());
        given(setGenerator.generate(any(), any(), any(), any())).willReturn(proposal());

        // when
        service.propose(CatalogSearchCriteria.none(), TARGET_MINUTES, SetCurve.STANDARD, null);

        // then
        verify(catalogService).search(any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize())
            .isEqualTo(SetProposalService.MAX_CANDIDATES);
    }

    @Test
    @DisplayName("override slotu DJ-a wygrywa z wyliczeniem także w puli kandydatów")
    void propose_whenSlotOverrideExists_prefersItOverCalculatedSlot() {

        // given
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID);
        givenCandidates(track);
        given(libraryEntryRepository.findDjData(anyList()))
            .willReturn(List.of(new TrackDjData(SPOTIFY_ID, 4, DjSlot.CLOSING.name())));
        given(setGenerator.generate(any(), any(), any(), any())).willReturn(proposal());

        // when
        service.propose(CatalogSearchCriteria.none(), TARGET_MINUTES, SetCurve.STANDARD, null);

        // then
        verify(setGenerator).generate(candidatesCaptor.capture(), any(), any(), any());
        SetCandidate candidate = candidatesCaptor.getValue().get(0);
        assertThat(candidate.slot()).isEqualTo(DjSlot.CLOSING);
        assertThat(candidate.rating()).isEqualTo(4);
        verify(djSlotCalculator, never()).calculate(any());
    }

    @Test
    @DisplayName("bez override slot kandydata liczy kalkulator")
    void propose_whenNoOverride_usesCalculatedSlot() {

        // given
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID);
        givenCandidates(track);
        given(libraryEntryRepository.findDjData(anyList())).willReturn(List.of());
        given(djSlotCalculator.calculate(track)).willReturn(Optional.of(DjSlot.PEAK));
        given(setGenerator.generate(any(), any(), any(), any())).willReturn(proposal());

        // when
        service.propose(CatalogSearchCriteria.none(), TARGET_MINUTES, SetCurve.STANDARD, null);

        // then
        verify(setGenerator).generate(candidatesCaptor.capture(), any(), any(), any());
        assertThat(candidatesCaptor.getValue().get(0).slot()).isEqualTo(DjSlot.PEAK);
    }

    @Test
    @DisplayName("długość celu idzie do generatora jako czas, nie jako liczba minut")
    void propose_passesTargetAsDuration() {

        // given
        givenCandidates(TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID));
        given(libraryEntryRepository.findDjData(anyList())).willReturn(List.of());
        given(setGenerator.generate(any(), any(), any(), any())).willReturn(proposal());

        // when
        service.propose(CatalogSearchCriteria.none(), TARGET_MINUTES, SetCurve.WEDDING, 7L);

        // then
        verify(setGenerator).generate(
            anyList(), eq(Duration.ofMinutes(TARGET_MINUTES)),
            eq(SetCurve.WEDDING),
            eq(7L));
    }

    @Test
    @DisplayName("uzupełnianie setu podaje obecny skład jako część już zajętą")
    void fill_reportsCurrentSetSizeAndDuration() {

        // given
        TrackCatalog inSet = TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID);
        given(playlistService.get(PLAYLIST_ID)).willReturn(planWith(inSet));
        givenCandidates(TrackCatalogFixtures.enrichedTrack("inny-utwor"));
        given(libraryEntryRepository.findDjData(anyList())).willReturn(List.of());
        given(setRules.durationMs(any())).willReturn(200_000L);
        given(setGenerator.extend(any(), any(), any(), any(), any())).willReturn(proposal());

        // when
        SetFill fill = service.fill(
            PLAYLIST_ID, CatalogSearchCriteria.none(), TARGET_MINUTES, SetCurve.STANDARD, null);

        // then
        assertThat(fill.currentTrackCount()).isEqualTo(1);
        assertThat(fill.currentDurationMs()).isEqualTo(200_000L);
    }

    @Test
    @DisplayName("ocena DJ-a utworów już w secie zostaje pusta — oceniamy kandydatów")
    void fill_leavesRatingEmptyForTracksAlreadyInSet() {

        // given
        TrackCatalog inSet = TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID);
        given(playlistService.get(PLAYLIST_ID)).willReturn(planWith(inSet));
        givenCandidates(TrackCatalogFixtures.enrichedTrack("inny-utwor"));
        given(libraryEntryRepository.findDjData(anyList())).willReturn(List.of());
        given(setRules.durationMs(any())).willReturn(0L);
        given(setGenerator.extend(any(), any(), any(), any(), any())).willReturn(proposal());

        // when
        service.fill(PLAYLIST_ID, CatalogSearchCriteria.none(), TARGET_MINUTES,
            SetCurve.STANDARD, null);

        // then
        verify(setGenerator).extend(anyList(), candidatesCaptor.capture(), any(), any(), any());
        assertThat(candidatesCaptor.getValue().get(0).rating()).isNull();
    }

    @Test
    @DisplayName("brak pozycji w dobieraniu znaczy „na koniec setu”")
    void suggest_whenPositionIsAbsent_appendsAtEnd() {

        // given
        given(playlistService.get(PLAYLIST_ID))
            .willReturn(planWith(TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID)));
        givenCandidates(TrackCatalogFixtures.enrichedTrack("inny-utwor"));
        given(libraryEntryRepository.findDjData(anyList())).willReturn(List.of());
        given(setSuggester.suggest(anyList(), anyList(), anyInt(), anyInt()))
            .willReturn(List.of());

        // when
        SetSuggestions suggestions = service.suggest(
            PLAYLIST_ID, CatalogSearchCriteria.none(), null, null);

        // then
        assertThat(suggestions.position()).isEqualTo(1);
    }

    @Test
    @DisplayName("pozycja spoza setu jest odrzucana")
    void suggest_whenPositionOutsideSet_throwsOutOfRange() {

        // given
        given(playlistService.get(PLAYLIST_ID))
            .willReturn(planWith(TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID)));

        // when
        Throwable thrown = catchThrowable(() -> service.suggest(
            PLAYLIST_ID, CatalogSearchCriteria.none(), 5, null));

        // then
        assertThat(thrown).isInstanceOf(ValidationException.class);
        assertThat(((ValidationException) thrown).getErrorCode())
            .isEqualTo("SET_POSITION_OUT_OF_RANGE");
    }

    @Test
    @DisplayName("brak limitu w dobieraniu schodzi do wartości domyślnej")
    void suggest_whenLimitIsAbsent_usesDefaultLimit() {

        // given
        given(playlistService.get(PLAYLIST_ID)).willReturn(planWith());
        givenCandidates(TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID));
        given(libraryEntryRepository.findDjData(anyList())).willReturn(List.of());
        given(setSuggester.suggest(anyList(), anyList(), anyInt(), anyInt()))
            .willReturn(List.of());

        // when
        service.suggest(PLAYLIST_ID, CatalogSearchCriteria.none(), null, null);

        // then
        verify(setSuggester).suggest(anyList(), anyList(), anyInt(),
            eq(SetSuggester.DEFAULT_LIMIT));
    }

    private void givenCandidates(TrackCatalog... tracks) {

        given(catalogService.search(any(), any(), any()))
            .willReturn(new PageImpl<>(List.of(tracks), PageRequest.of(0, 2000), tracks.length));
    }

    private PlaylistPlan planWith(TrackCatalog... tracks) {

        Playlist playlist = new Playlist("Wesele");
        List<PlannedTrack> planned = IntStream.range(0, tracks.length)
            .mapToObj(position ->
                new PlannedTrack(position, tracks[position], DjSlot.PEAK, null, null))
            .toList();
        return new PlaylistPlan(playlist, planned);
    }

    private SetProposal proposal() {
        return new SetProposal(List.of(), 0L, 0L, 1L, List.of());
    }
}
