package com.pgoogol.music.catalog;

import com.pgoogol.music.common.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockingDetails;

/**
 * Serwis katalogu tłumaczy kryteria z UI na parametry jednego zapytania
 * natywnego — i to tłumaczenie jest tu badane, bo właśnie ono zmienia sens
 * filtra (sekundy na milisekundy, pozycja koła na listę zapisów tonacji).
 */
@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    private static final String SPOTIFY_ID = "4uLU6hMCjMI75M1A2tKUQC";
    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Mock
    private TrackCatalogRepository trackCatalogRepository;

    @Mock
    private ManualMetricsRepository manualMetricsRepository;

    @InjectMocks
    private CatalogService service;

    @Test
    @DisplayName("getTrack zwraca utwór, gdy jest w katalogu")
    void getTrack_whenTrackExists_returnsTrack() {

        // given
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID);
        given(trackCatalogRepository.findById(SPOTIFY_ID)).willReturn(Optional.of(track));

        // when
        TrackCatalog found = service.getTrack(SPOTIFY_ID);

        // then
        assertThat(found).isSameAs(track);
    }

    @Test
    @DisplayName("getTrack rzuca TRACK_NOT_FOUND, gdy utworu nie ma")
    void getTrack_whenTrackMissing_throwsNotFoundException() {

        // given
        given(trackCatalogRepository.findById(SPOTIFY_ID)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.getTrack(SPOTIFY_ID));

        // then
        assertThat(thrown)
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining(SPOTIFY_ID);
        assertThat(((NotFoundException) thrown).getErrorCode()).isEqualTo("TRACK_NOT_FOUND");
    }

    @Test
    @DisplayName("getTrack odrzuca brak identyfikatora zamiast pytać bazę o null")
    void getTrack_whenSpotifyIdIsNull_throwsNullPointerException() {

        // when / then
        assertThatThrownBy(() -> service.getTrack(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("spotifyId");
    }

    @Test
    @DisplayName("findMetrics oddaje pustą wartość, gdy utwór nie dostał metryk")
    void findMetrics_whenTrackHasNoMetrics_returnsEmpty() {

        // given
        given(manualMetricsRepository.findById(SPOTIFY_ID)).willReturn(Optional.empty());

        // when
        Optional<ManualMetrics> metrics = service.findMetrics(SPOTIFY_ID);

        // then
        assertThat(metrics).isEmpty();
    }

    @Test
    @DisplayName("metricsCoverage zestawia liczbę utworów z metrykami z całością katalogu")
    void metricsCoverage_reportsBothCounts() {

        // given
        given(trackCatalogRepository.countWithMetrics()).willReturn(120L);
        given(trackCatalogRepository.count()).willReturn(2500L);

        // when
        CatalogService.MetricsCoverage coverage = service.metricsCoverage();

        // then
        assertThat(coverage.withMetrics()).isEqualTo(120L);
        assertThat(coverage.total()).isEqualTo(2500L);
    }

    @Test
    @DisplayName("puste szukane hasło znaczy brak filtra, nie dopasowanie pustego tekstu")
    void search_whenSearchTextIsBlank_passesNoTextFilter() {

        // given
        givenEmptyPage();
        CatalogSearchCriteria criteria = new CatalogSearchCriteria(
            "   ", null, null, null, null, null);

        // when
        service.search(criteria, CatalogSortOrder.DEFAULT, PAGE);

        // then
        assertThat(capturedSearch().search()).isNull();
    }

    @Test
    @DisplayName("szukane hasło traci otaczające spacje")
    void search_whenSearchTextHasPadding_passesStrippedText() {

        // given
        givenEmptyPage();
        CatalogSearchCriteria criteria = new CatalogSearchCriteria(
            "  celia cruz  ", null, null, null, null, null);

        // when
        service.search(criteria, CatalogSortOrder.DEFAULT, PAGE);

        // then
        assertThat(capturedSearch().search()).isEqualTo("celia cruz");
    }

    @Test
    @DisplayName("długość z UI idzie do zapytania w milisekundach")
    void search_convertsDurationFromSecondsToMillis() {

        // given
        givenEmptyPage();
        CatalogSearchCriteria criteria = criteriaWithDuration(120, 300);

        // when
        service.search(criteria, CatalogSortOrder.DEFAULT, PAGE);

        // then
        SearchArgs args = capturedSearch();
        assertThat(args.durationMinMs()).isEqualTo(120_000);
        assertThat(args.durationMaxMs()).isEqualTo(300_000);
    }

    @Test
    @DisplayName("długość ponad dobę przycina się do sufitu, zamiast przekręcić int")
    void search_whenDurationExceedsOneDay_clampsToCeiling() {

        // given — bez sufitu 999999999 * 1000 przelewa int i filtr znaczy co innego
        givenEmptyPage();
        CatalogSearchCriteria criteria = criteriaWithDuration(-5, 999_999_999);

        // when
        service.search(criteria, CatalogSortOrder.DEFAULT, PAGE);

        // then
        SearchArgs args = capturedSearch();
        assertThat(args.durationMinMs()).isZero();
        assertThat(args.durationMaxMs()).isEqualTo(24 * 60 * 60 * 1000);
        assertThat(args.durationMaxMs()).isPositive();
    }

    @Test
    @DisplayName("filtr harmoniczny bez zgodnych rozwija tylko wskazaną tonację")
    void search_whenHarmonicIsExact_expandsOnlyThatKey() {

        // given — 8A to a-moll
        givenEmptyPage();
        CatalogSearchCriteria criteria = criteriaWithHarmonic(new CamelotKey(8, true), false);

        // when
        service.search(criteria, CatalogSortOrder.DEFAULT, PAGE);

        // then
        assertThat(capturedSearch().musicalKeys().split("\\|"))
            .containsExactlyInAnyOrderElementsOf(new CamelotKey(8, true).musicalKeySpellings());
    }

    @Test
    @DisplayName("filtr harmoniczny ze zgodnymi obejmuje sąsiadów koła i tonację równoległą")
    void search_whenHarmonicIsCompatible_expandsToCompatibleKeys() {

        // given
        givenEmptyPage();
        CamelotKey key = new CamelotKey(8, true);
        CatalogSearchCriteria criteria = criteriaWithHarmonic(key, true);

        // when
        service.search(criteria, CatalogSortOrder.DEFAULT, PAGE);

        // then
        List<String> expected = key.compatible().stream()
            .map(CamelotKey::musicalKeySpellings)
            .flatMap(List::stream)
            .distinct()
            .toList();
        assertThat(capturedSearch().musicalKeys().split("\\|"))
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @DisplayName("brak filtra harmonicznego nie zawęża tonacji")
    void search_whenNoHarmonicFilter_passesNoMusicalKeys() {

        // given
        givenEmptyPage();

        // when
        service.search(CatalogSearchCriteria.none(), CatalogSortOrder.DEFAULT, PAGE);

        // then
        assertThat(capturedSearch().musicalKeys()).isNull();
    }

    @Test
    @DisplayName("filtry wyliczeniowe idą do zapytania jako nazwy stałych")
    void search_mapsEnumFiltersToConstantNames() {

        // given
        givenEmptyPage();
        CatalogSearchCriteria criteria = new CatalogSearchCriteria(
            null,
            new CatalogSearchCriteria.TrackFilter(
                GenreFamily.LATIN, null, null, null, null, null, null),
            null,
            null,
            null,
            new CatalogSearchCriteria.QualityFilter(BpmSource.ACOUSTICBRAINZ, MissingGroup.AUDIO));

        // when
        service.search(criteria, CatalogSortOrder.DEFAULT, PAGE);

        // then
        SearchArgs args = capturedSearch();
        assertThat(args.genreFamily()).isEqualTo(GenreFamily.LATIN.name());
        assertThat(args.bpmSource()).isEqualTo(BpmSource.ACOUSTICBRAINZ.name());
        assertThat(args.missing()).isEqualTo(MissingGroup.AUDIO.name());
    }

    @Test
    @DisplayName("porządek sortowania trafia do zapytania jako pole i kierunek")
    void search_passesSortFieldAndDirection() {

        // given
        givenEmptyPage();
        CatalogSortOrder sortOrder =
            new CatalogSortOrder(CatalogSort.BPM, Sort.Direction.DESC);

        // when
        service.search(CatalogSearchCriteria.none(), sortOrder, PAGE);

        // then
        SearchArgs args = capturedSearch();
        assertThat(args.sort()).isEqualTo(CatalogSort.BPM.name());
        assertThat(args.direction()).isEqualTo(Sort.Direction.DESC.name());
    }

    @Test
    @DisplayName("search odrzuca brak kryteriów zamiast budować zapytanie z null-a")
    void search_whenCriteriaIsNull_throwsNullPointerException() {

        // when / then
        assertThatThrownBy(() -> service.search(null, CatalogSortOrder.DEFAULT, PAGE))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("criteria");
    }

    private void givenEmptyPage() {

        given(trackCatalogRepository.search(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            anyString(), anyString(), any()))
            .willReturn(Page.empty());
    }

    private CatalogSearchCriteria criteriaWithDuration(Integer minSec, Integer maxSec) {

        return new CatalogSearchCriteria(
            null,
            new CatalogSearchCriteria.TrackFilter(null, null, null, minSec, maxSec, null, null),
            null, null, null, null);
    }

    private CatalogSearchCriteria criteriaWithHarmonic(CamelotKey key, boolean compatible) {

        return new CatalogSearchCriteria(
            null,
            null,
            new CatalogSearchCriteria.SoundFilter(null, null, null, null,
                new CatalogSearchCriteria.HarmonicFilter(key, compatible)),
            null, null, null);
    }

    /**
     * Zapytanie wyszukiwarki bierze 25 argumentów, więc osobny captor na każdy
     * pozycję zamieniłby test w ścianę {@code any()}. Czytamy argumenty
     * z zarejestrowanego wywołania i nazywamy tylko te, o które pyta dany test.
     */
    private SearchArgs capturedSearch() {

        Object[] args = mockingDetails(trackCatalogRepository).getInvocations().stream()
            .filter(invocation -> "search".equals(invocation.getMethod().getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("zapytanie wyszukiwarki nie poszło do bazy"))
            .getArguments();
        return new SearchArgs(
            (String) args[0],
            (String) args[1],
            (Integer) args[4],
            (Integer) args[5],
            (String) args[12],
            (String) args[20],
            (String) args[21],
            (String) args[22],
            (String) args[23]);
    }

    private record SearchArgs(String search,
                              String genreFamily,
                              Integer durationMinMs,
                              Integer durationMaxMs,
                              String musicalKeys,
                              String bpmSource,
                              String missing,
                              String sort,
                              String direction) { }
}
