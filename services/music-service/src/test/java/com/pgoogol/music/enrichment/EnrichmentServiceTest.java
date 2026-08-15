package com.pgoogol.music.enrichment;

import com.pgoogol.music.config.EnrichmentJobConfig;
import com.pgoogol.music.catalog.TrackCatalogRepository;
import com.pgoogol.music.common.AppException;
import com.pgoogol.music.common.NotFoundException;
import com.pgoogol.music.enrichment.llm.LlmProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.explore.JobExplorer;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Zlecenia wzbogacania. Najciekawsze jest to, czego serwis broni: sufit kosztowy
 * liczy się po utworach idących do LLM-a (a nie po wielkości przebiegu), zakres
 * OUTDATED przelicza wyłącznie estymaty, a lista id-ków ma osobny limit wynikający
 * z szerokości kolumny parametru joba — nie z ceny.
 */
@ExtendWith(MockitoExtension.class)
class EnrichmentServiceTest {

    private static final String SPOTIFY_ID = "4uLU6hMCjMI75M1A2tKUQC";
    private static final long EXECUTION_ID = 7L;

    @Mock
    private JobLauncher asyncJobLauncher;

    @Mock
    private Job enrichmentJob;

    @Mock
    private JobExplorer jobExplorer;

    @Mock
    private EnrichmentJobHistory jobHistory;

    @Mock
    private TrackCatalogRepository trackCatalogRepository;

    @Mock
    private EnrichmentCostEstimator costEstimator;

    @Mock
    private EnrichmentFailureRepository failureRepository;

    @Captor
    private ArgumentCaptor<JobParameters> parametersCaptor;

    @Test
    @DisplayName("zakres SINGLE wymaga dokładnie jednego identyfikatora")
    void estimate_whenSingleScopeHasManyIds_throwsIdsRequired() {

        // when
        Throwable thrown = catchThrowable(() -> service(properties("gpt-5", 500))
            .estimate(EnrichmentScope.SINGLE, Set.of(FieldGroup.AI), List.of("a", "b")));

        // then
        assertThat(errorCode(thrown)).isEqualTo("ENRICH_IDS_REQUIRED");
    }

    @Test
    @DisplayName("pusty zestaw grup pól jest odrzucany")
    void estimate_whenNoFieldGroupSelected_throwsFieldsEmpty() {

        // when
        Throwable thrown = catchThrowable(() -> service(properties("gpt-5", 500))
            .estimate(EnrichmentScope.MISSING, Set.of(), List.of()));

        // then
        assertThat(errorCode(thrown)).isEqualTo("ENRICH_FIELDS_EMPTY");
    }

    @Test
    @DisplayName("zakres MISSING nie przyjmuje listy identyfikatorów")
    void estimate_whenMissingScopeCarriesIds_throwsIdsUnexpected() {

        // when
        Throwable thrown = catchThrowable(() -> service(properties("gpt-5", 500))
            .estimate(EnrichmentScope.MISSING, Set.of(FieldGroup.AI), List.of(SPOTIFY_ID)));

        // then
        assertThat(errorCode(thrown)).isEqualTo("ENRICH_IDS_UNEXPECTED");
    }

    @Test
    @DisplayName("zakres OUTDATED przelicza wyłącznie estymaty AI")
    void estimate_whenOutdatedScopeAsksForFacts_throwsAiOnly() {

        // when — metadane nie zależą od modelu, więc ich przeliczanie to cudze API bez powodu
        Throwable thrown = catchThrowable(() -> service(properties("gpt-5", 500))
            .estimate(EnrichmentScope.OUTDATED,
                Set.of(FieldGroup.AI, FieldGroup.METADATA), List.of()));

        // then
        assertThat(errorCode(thrown)).isEqualTo("ENRICH_OUTDATED_AI_ONLY");
    }

    @Test
    @DisplayName("zakres OUTDATED bez skonfigurowanego modelu kończy się jasnym błędem")
    void estimate_whenOutdatedScopeAndModelMissing_throwsModelNotConfigured() {

        // when
        Throwable thrown = catchThrowable(() -> service(properties("  ", 500))
            .estimate(EnrichmentScope.OUTDATED, Set.of(FieldGroup.AI), List.of()));

        // then
        assertThat(errorCode(thrown)).isEqualTo("LLM_MODEL_NOT_CONFIGURED");
    }

    @Test
    @DisplayName("podejrzany spotify_id nie trafia do parametru joba")
    void estimate_whenSpotifyIdHasIllegalCharacters_throwsBadId() {

        // when — parametr joba wchodzi do klauzuli WHERE readera
        Throwable thrown = catchThrowable(() -> service(properties("gpt-5", 500))
            .estimate(EnrichmentScope.SINGLE, Set.of(FieldGroup.AI), List.of("'; drop table --")));

        // then
        assertThat(errorCode(thrown)).isEqualTo("ENRICH_BAD_ID");
    }

    @Test
    @DisplayName("lista SELECTED ponad sufit kolumny parametru jest odrzucana")
    void estimate_whenSelectedListExceedsColumnLimit_throwsTooManyTracks() {

        // given
        List<String> tooMany = Collections.nCopies(
            EnrichmentService.MAX_SELECTED_TRACKS + 1, SPOTIFY_ID);

        // when
        Throwable thrown = catchThrowable(() -> service(properties("gpt-5", 500))
            .estimate(EnrichmentScope.SELECTED, Set.of(FieldGroup.AI), tooMany));

        // then
        assertThat(errorCode(thrown)).isEqualTo("ENRICH_TOO_MANY_TRACKS");
    }

    @Test
    @DisplayName("sufit kosztowy liczy utwory idące do LLM-a, nie wielkość przebiegu")
    void estimate_whenOnlyFactsRequested_reportsNoAiTracksAndStaysWithinLimit() {

        // given — 5000 utworów bez grupy AI mieści się w limicie 500, bo AI ich nie dotyczy
        given(trackCatalogRepository.countMissingForFields(true, true, false)).willReturn(5000L);

        // when
        EnrichmentEstimate estimate = service(properties("gpt-5", 500)).estimate(
            EnrichmentScope.MISSING, Set.of(FieldGroup.METADATA, FieldGroup.AUDIO), List.of());

        // then
        assertThat(estimate.trackCount()).isEqualTo(5000L);
        assertThat(estimate.aiTracks()).isZero();
        assertThat(estimate.withinLimit()).isTrue();
    }

    @Test
    @DisplayName("grupa AI ponad limit oznacza szacunek poza sufitem")
    void estimate_whenAiTracksExceedLimit_marksEstimateOutsideLimit() {

        // given
        given(trackCatalogRepository.countMissingForFields(false, false, true)).willReturn(900L);
        given(costEstimator.estimate(900L)).willReturn(Optional.of(new BigDecimal("12.34")));

        // when
        EnrichmentEstimate estimate = service(properties("gpt-5", 500))
            .estimate(EnrichmentScope.MISSING, Set.of(FieldGroup.AI), List.of());

        // then
        assertThat(estimate.aiTracks()).isEqualTo(900L);
        assertThat(estimate.withinLimit()).isFalse();
        assertThat(estimate.estimatedCost()).isEqualByComparingTo("12.34");
    }

    @Test
    @DisplayName("nieznany cennik daje pusty koszt zamiast zgadywanej liczby")
    void estimate_whenPricingUnknown_leavesCostEmpty() {

        // given
        given(trackCatalogRepository.countMissingForFields(false, false, true)).willReturn(10L);
        given(costEstimator.estimate(10L)).willReturn(Optional.empty());

        // when
        EnrichmentEstimate estimate = service(properties("gpt-5", 500))
            .estimate(EnrichmentScope.MISSING, Set.of(FieldGroup.AI), List.of());

        // then
        assertThat(estimate.estimatedCost()).isNull();
    }

    @Test
    @DisplayName("start ponad sufitem nie uruchamia joba")
    void start_whenEstimateExceedsLimit_throwsWithoutLaunching() throws Exception {

        // given
        given(trackCatalogRepository.countMissingForFields(false, false, true)).willReturn(900L);
        given(costEstimator.estimate(900L)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service(properties("gpt-5", 500))
            .start(EnrichmentScope.MISSING, Set.of(FieldGroup.AI), List.of()));

        // then
        assertThat(errorCode(thrown)).isEqualTo("ENRICH_TOO_MANY_TRACKS");
        verify(asyncJobLauncher, never()).run(any(), any());
    }

    @Test
    @DisplayName("grupy pól idą do parametrów joba w stałej kolejności")
    void start_writesFieldGroupsInCanonicalOrder() throws Exception {

        // given — kolejność decyduje o tożsamości joba, więc restart musi ją odtworzyć
        JobExecution started = execution(EXECUTION_ID);
        given(trackCatalogRepository.countMissingForFields(true, true, false)).willReturn(3L);
        given(asyncJobLauncher.run(any(), any())).willReturn(started);

        // when
        service(properties("gpt-5", 500)).start(EnrichmentScope.MISSING,
            EnumSet.of(FieldGroup.AUDIO, FieldGroup.METADATA), List.of());

        // then
        verify(asyncJobLauncher).run(any(), parametersCaptor.capture());
        assertThat(parametersCaptor.getValue().getString("fields")).isEqualTo("METADATA,AUDIO");
    }

    @Test
    @DisplayName("zakres OUTDATED zapisuje model w tożsamości joba")
    void start_whenOutdatedScope_putsModelIntoJobParameters() throws Exception {

        // given
        JobExecution started = execution(EXECUTION_ID);
        given(trackCatalogRepository.countOutdated(any(), any())).willReturn(3L);
        given(costEstimator.estimate(3L)).willReturn(Optional.empty());
        given(asyncJobLauncher.run(any(), any())).willReturn(started);

        // when
        service(properties("gpt-5", 500))
            .start(EnrichmentScope.OUTDATED, Set.of(FieldGroup.AI), List.of());

        // then
        verify(asyncJobLauncher).run(any(), parametersCaptor.capture());
        assertThat(parametersCaptor.getValue().getString("outdatedModel")).isEqualTo("gpt-5");
    }

    @Test
    @DisplayName("restart zakończonego wykonania jest odrzucany")
    void restart_whenExecutionIsNotRestartable_throwsNotRestartable() {

        // given
        JobExecution completed = execution(EXECUTION_ID);
        given(completed.getStatus()).willReturn(BatchStatus.COMPLETED);
        given(jobExplorer.getJobExecution(EXECUTION_ID)).willReturn(completed);

        // when
        Throwable thrown = catchThrowable(
            () -> service(properties("gpt-5", 500)).restart(EXECUTION_ID));

        // then
        assertThat(errorCode(thrown)).isEqualTo("JOB_NOT_RESTARTABLE");
    }

    @Test
    @DisplayName("pytanie o nieznane wykonanie kończy się JOB_NOT_FOUND")
    void status_whenExecutionUnknown_throwsNotFound() {

        // given
        given(jobExplorer.getJobExecution(anyLong())).willReturn(null);

        // when
        Throwable thrown = catchThrowable(
            () -> service(properties("gpt-5", 500)).status(EXECUTION_ID));

        // then
        assertThat(thrown).isInstanceOf(NotFoundException.class);
        assertThat(errorCode(thrown)).isEqualTo("JOB_NOT_FOUND");
    }

    @Test
    @DisplayName("wykonanie innego joba nie jest widziane jako wzbogacanie")
    void status_whenExecutionBelongsToAnotherJob_throwsNotFound() {

        // given
        JobExecution foreign = execution(EXECUTION_ID);
        given(foreign.getJobInstance()).willReturn(new JobInstance(1L, "innyJob"));
        given(jobExplorer.getJobExecution(EXECUTION_ID)).willReturn(foreign);

        // when
        Throwable thrown = catchThrowable(
            () -> service(properties("gpt-5", 500)).status(EXECUTION_ID));

        // then
        assertThat(errorCode(thrown)).isEqualTo("JOB_NOT_FOUND");
    }

    @Test
    @DisplayName("lista wykonań nigdy nie schodzi poniżej jednego wiersza")
    void listJobs_clampsNonPositiveLimitToOne() {

        // given
        given(jobHistory.recent(EnrichmentJobConfig.JOB_NAME, 1)).willReturn(List.of());

        // when
        service(properties("gpt-5", 500)).listJobs(0);

        // then
        verify(jobHistory).recent(EnrichmentJobConfig.JOB_NAME, 1);
    }

    @Test
    @DisplayName("missingCount przepisuje liczniki z jednego zapytania katalogu")
    void missingCount_mapsCountsFromCatalog() {

        // given
        given(trackCatalogRepository.countMissingByGroup()).willReturn(missingCounts(3, 5, 8));

        // when
        MissingFieldsCount counts = service(properties("gpt-5", 500)).missingCount();

        // then
        assertThat(counts.metadata()).isEqualTo(3);
        assertThat(counts.audio()).isEqualTo(5);
        assertThat(counts.ai()).isEqualTo(8);
    }

    private EnrichmentService service(LlmProperties properties) {

        return new EnrichmentService(asyncJobLauncher, enrichmentJob, jobExplorer, jobHistory,
            trackCatalogRepository, costEstimator, failureRepository, properties);
    }

    private LlmProperties properties(String model, int maxTracksPerJob) {

        return new LlmProperties("openai", null, null, model, "v1", 5, 2, 2048, 0.2,
            maxTracksPerJob, null);
    }

    /**
     * {@link JobExecution} jest klasą Batcha z sporym stanem wewnętrznym —
     * mock daje tu czytelniejszy test niż budowanie prawdziwego wykonania.
     */
    private JobExecution execution(long id) {

        JobExecution execution = mock(JobExecution.class);
        lenient().when(execution.getId()).thenReturn(id);
        lenient().when(execution.getJobInstance())
            .thenReturn(new JobInstance(1L, EnrichmentJobConfig.JOB_NAME));
        return execution;
    }

    private TrackCatalogRepository.MissingCounts missingCounts(long metadata, long audio, long ai) {

        return new TrackCatalogRepository.MissingCounts() {

            @Override
            public long getMetadata() {
                return metadata;
            }

            @Override
            public long getAudio() {
                return audio;
            }

            @Override
            public long getAi() {
                return ai;
            }
        };
    }

    private String errorCode(Throwable thrown) {

        assertThat(thrown).isInstanceOf(AppException.class);
        return ((AppException) thrown).getErrorCode();
    }
}
