package com.pgoogol.music.ingestion;

import com.pgoogol.music.catalog.GenreFamily;
import com.pgoogol.music.catalog.ManualMetrics;
import com.pgoogol.music.enrichment.metrics.ManualMetricsApplier;
import com.pgoogol.music.catalog.ManualMetricsRepository;
import com.pgoogol.music.catalog.TrackCatalog;
import com.pgoogol.music.catalog.TrackCatalogFixtures;
import com.pgoogol.music.catalog.TrackCatalogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Import metryk z pliku. Dopasowanie idzie po spotify_id, a gdy go brak —
 * po ISRC, który identyfikuje nagranie, więc trafia do wszystkich jego wydań.
 * Wiersz bez odpowiednika w katalogu jest pomijany z raportem, nie zakłada
 * utworu: biblioteka jedzie ze Spotify, plik ma tylko dołożyć metryki.
 */
@ExtendWith(MockitoExtension.class)
class MetricsIngestionServiceTest {

    private static final String SPOTIFY_ID = "4uLU6hMCjMI75M1A2tKUQC";
    private static final String OTHER_ID = "1nZzLMBiPPUlYAcZDkCsSN";
    private static final String ISRC = "USSD11300483";

    @Mock
    private MetricsCsvParser parser;

    @Mock
    private TrackCatalogRepository trackCatalogRepository;

    @Mock
    private ManualMetricsRepository manualMetricsRepository;

    @Mock
    private ManualMetricsApplier applier;

    @InjectMocks
    private MetricsIngestionService service;

    @Captor
    private ArgumentCaptor<Collection<ManualMetrics>> savedCaptor;

    @Test
    @DisplayName("wiersz z rozpoznanym spotify_id zapisuje metryki dla tego utworu")
    void ingest_whenRowMatchesBySpotifyId_savesMetricsForThatTrack() {

        // given
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID);
        givenParsed(row(1, SPOTIFY_ID, null));
        given(trackCatalogRepository.findAllById(Set.of(SPOTIFY_ID))).willReturn(List.of(track));
        given(manualMetricsRepository.findBySpotifyIdIn(anySet())).willReturn(List.of());

        // when
        MetricsIngestReport report = service.ingest(csv(), "rekordbox");

        // then
        assertThat(report.applied()).isEqualTo(1);
        assertThat(report.matchedByIsrc()).isZero();
        assertThat(report.skipped()).isEmpty();
    }

    @Test
    @DisplayName("wiersz bez spotify_id dopasowuje się po ISRC do wszystkich wydań nagrania")
    void ingest_whenRowMatchesByIsrc_appliesMetricsToEveryRelease() {

        // given — to samo nagranie wydane na singlu i na albumie
        TrackCatalog single = trackWithIsrc(SPOTIFY_ID);
        TrackCatalog album = trackWithIsrc(OTHER_ID);
        givenParsed(row(1, null, ISRC));
        given(trackCatalogRepository.findByIsrcInIgnoreCase(Set.of(ISRC)))
            .willReturn(List.of(single, album));
        given(manualMetricsRepository.findBySpotifyIdIn(anySet())).willReturn(List.of());

        // when
        MetricsIngestReport report = service.ingest(csv(), null);

        // then
        assertThat(report.applied()).isEqualTo(2);
        assertThat(report.matchedByIsrc()).isEqualTo(1);
    }

    @Test
    @DisplayName("spotify_id ma pierwszeństwo przed ISRC")
    void ingest_whenBothIdentifiersPresent_prefersSpotifyId() {

        // given
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID);
        givenParsed(row(1, SPOTIFY_ID, ISRC));
        given(trackCatalogRepository.findAllById(Set.of(SPOTIFY_ID))).willReturn(List.of(track));
        lenient().when(trackCatalogRepository.findByIsrcInIgnoreCase(anySet()))
            .thenReturn(List.of(trackWithIsrc(OTHER_ID)));
        given(manualMetricsRepository.findBySpotifyIdIn(anySet())).willReturn(List.of());

        // when
        MetricsIngestReport report = service.ingest(csv(), null);

        // then
        assertThat(report.applied()).isEqualTo(1);
        assertThat(report.matchedByIsrc()).isZero();
    }

    @Test
    @DisplayName("wiersz bez odpowiednika w katalogu jest pomijany, a nie zakłada utworu")
    void ingest_whenTrackNotInCatalog_reportsSkippedWithoutCreatingIt() {

        // given
        givenParsed(row(4, SPOTIFY_ID, null));
        given(trackCatalogRepository.findAllById(Set.of(SPOTIFY_ID))).willReturn(List.of());

        // when
        MetricsIngestReport report = service.ingest(csv(), null);

        // then
        assertThat(report.applied()).isZero();
        assertThat(report.skipped()).hasSize(1);
        assertThat(report.skipped().get(0).line()).isEqualTo(4);
        verify(trackCatalogRepository, never()).save(any());
    }

    @Test
    @DisplayName("kolumna nieobecna w pliku kasuje poprzednią wartość metryki")
    void ingest_whenColumnAbsent_clearsPreviousValue() {

        // given — plik jest źródłem prawdy dla metryk
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID);
        ManualMetrics existing = persistedMetrics(track);
        existing.setValence(new BigDecimal("0.900"));
        givenParsed(new ParsedMetrics(1, SPOTIFY_ID, null, null, emptyMetrics()));
        given(trackCatalogRepository.findAllById(Set.of(SPOTIFY_ID))).willReturn(List.of(track));
        given(manualMetricsRepository.findBySpotifyIdIn(anySet())).willReturn(List.of(existing));

        // when
        service.ingest(csv(), null);

        // then
        verify(manualMetricsRepository).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue().iterator().next().getValence()).isNull();
    }

    @Test
    @DisplayName("ponowny import nadpisuje istniejący wiersz metryk zamiast dokładać drugi")
    void ingest_whenMetricsAlreadyExist_updatesThemInPlace() {

        // given
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID);
        ManualMetrics existing = persistedMetrics(track);
        givenParsed(row(1, SPOTIFY_ID, null));
        given(trackCatalogRepository.findAllById(Set.of(SPOTIFY_ID))).willReturn(List.of(track));
        given(manualMetricsRepository.findBySpotifyIdIn(anySet())).willReturn(List.of(existing));

        // when
        service.ingest(csv(), null);

        // then
        verify(manualMetricsRepository).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue()).hasSize(1);
        assertThat(savedCaptor.getValue().iterator().next()).isSameAs(existing);
    }

    @Test
    @DisplayName("rodzina gatunkowa z pliku ląduje obok metryk, nie w katalogu")
    void ingest_storesGenreFamilyWithMetrics() {

        // given
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID);
        givenParsed(new ParsedMetrics(1, SPOTIFY_ID, null, GenreFamily.LATIN, emptyMetrics()));
        given(trackCatalogRepository.findAllById(Set.of(SPOTIFY_ID))).willReturn(List.of(track));
        given(manualMetricsRepository.findBySpotifyIdIn(anySet())).willReturn(List.of());

        // when
        service.ingest(csv(), null);

        // then
        verify(manualMetricsRepository).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue().iterator().next().getGenreFamily())
            .isEqualTo(GenreFamily.LATIN);
    }

    @Test
    @DisplayName("źródło importu zapisuje się przy metrykach")
    void ingest_storesSourceLabel() {

        // given
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID);
        givenParsed(row(1, SPOTIFY_ID, null));
        given(trackCatalogRepository.findAllById(Set.of(SPOTIFY_ID))).willReturn(List.of(track));
        given(manualMetricsRepository.findBySpotifyIdIn(anySet())).willReturn(List.of());

        // when
        service.ingest(csv(), "rekordbox");

        // then
        verify(manualMetricsRepository).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue().iterator().next().getSource()).isEqualTo("rekordbox");
    }

    @Test
    @DisplayName("metryki są rzutowane na katalog przez applier")
    void ingest_projectsMetricsOntoCatalogTrack() {

        // given
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID);
        givenParsed(row(1, SPOTIFY_ID, null));
        given(trackCatalogRepository.findAllById(Set.of(SPOTIFY_ID))).willReturn(List.of(track));
        given(manualMetricsRepository.findBySpotifyIdIn(anySet())).willReturn(List.of());

        // when
        service.ingest(csv(), null);

        // then
        verify(applier).apply(any(), any());
    }

    @Test
    @DisplayName("błędy wierszy z parsera trafiają do raportu")
    void ingest_passesParserErrorsToReport() {

        // given
        RowError error = new RowError(9, "nieczytelna wartość bpm");
        given(parser.parse(any())).willReturn(new MetricsParseResult(List.of(), List.of(error)));

        // when
        MetricsIngestReport report = service.ingest(csv(), null);

        // then
        assertThat(report.failed()).containsExactly(error);
        assertThat(report.applied()).isZero();
    }

    @Test
    @DisplayName("pusty plik nie odpytuje katalogu")
    void ingest_whenFileHasNoRows_skipsCatalogLookups() {

        // given
        given(parser.parse(any())).willReturn(new MetricsParseResult(List.of(), List.of()));

        // when
        MetricsIngestReport report = service.ingest(csv(), null);

        // then
        assertThat(report.applied()).isZero();
        verify(trackCatalogRepository, never()).findAllById(anySet());
        verify(trackCatalogRepository, never()).findByIsrcInIgnoreCase(anySet());
    }

    private void givenParsed(ParsedMetrics... rows) {

        given(parser.parse(any())).willReturn(new MetricsParseResult(List.of(rows), List.of()));
    }

    private ParsedMetrics row(long line, String spotifyId, String isrc) {

        return new ParsedMetrics(line, spotifyId, isrc, null, new TrackMetrics(
            new BigDecimal("92"), "A minor", "8A", new BigDecimal("0.850"),
            new BigDecimal("0.800"), new BigDecimal("0.700"), new BigDecimal("0.100"),
            new BigDecimal("0.050"), new BigDecimal("0.060"), new BigDecimal("0.120"),
            new BigDecimal("-6.5"), 4));
    }

    private TrackMetrics emptyMetrics() {

        return new TrackMetrics(null, null, null, null, null, null, null, null, null, null,
            null, null);
    }

    /**
     * {@code spotifyId} wypełnia JPA przy zapisie (@MapsId), więc świeży obiekt
     * ma tam null. Wiersz wczytany z bazy — a tylko taki trafia do scalania —
     * ma go ustawionego.
     */
    private ManualMetrics persistedMetrics(TrackCatalog track) {

        ManualMetrics metrics = new ManualMetrics(track);
        ReflectionTestUtils.setField(metrics, "spotifyId", track.getSpotifyId());
        return metrics;
    }

    private TrackCatalog trackWithIsrc(String spotifyId) {

        TrackCatalog track = TrackCatalogFixtures.skeletonTrack(spotifyId);
        track.setIsrc(ISRC);
        return track;
    }

    private InputStream csv() {

        return new ByteArrayInputStream("spotify_id\n".getBytes(StandardCharsets.UTF_8));
    }
}
