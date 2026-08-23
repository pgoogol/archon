package com.pgoogol.music.enrichment.bpm;

import com.pgoogol.music.catalog.AudioFeatures;
import com.pgoogol.music.catalog.AudioFeaturesRepository;
import com.pgoogol.music.catalog.BpmSource;
import com.pgoogol.music.catalog.ManualMetrics;
import com.pgoogol.music.catalog.ManualMetricsRepository;
import com.pgoogol.music.catalog.TrackCatalog;
import com.pgoogol.music.enrichment.deezer.DeezerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Kaskada BPM: metryki wgrane ręcznie →
 * {@code audio_features} (AcousticBrainz) → Deezer
 * (ISRC, potem artist+title) → brak (pole zostaje dla AI, M1.5/M1.6).
 * Sanity-check half-time (§16.1) stosowany do każdego źródła: latin + BPM &lt; 100
 * → podwojenie, o ile wynik pozostaje wiarygodny taneczne. Resolver nie zapisuje
 * do bazy — persystencja należy do writera joba wzbogacania (M1.6).
 */
@Component
@RequiredArgsConstructor
public class BpmResolver {

    private final ManualMetricsRepository manualMetricsRepository;
    private final AudioFeaturesRepository audioFeaturesRepository;
    private final DeezerClient deezerClient;
    private final HalfTimeCorrector halfTimeCorrector;

    public Optional<BpmResolution> resolve(TrackCatalog track) {

        Objects.requireNonNull(track, "track");
        return fromManual(track)
            .or(() -> fromAcousticBrainz(track))
            .or(() -> fromDeezer(track))
            .map(resolution -> withHalfTimeCorrection(track, resolution));
    }

    /** Raport pokrycia per źródło dla próbki utworów (DoD M1.4). */
    public BpmCoverageReport coverage(List<TrackCatalog> tracks) {

        Objects.requireNonNull(tracks, "tracks");
        Map<BpmSource, Long> counts = tracks.stream()
            .map(this::resolve)
            .flatMap(Optional::stream)
            .collect(Collectors.groupingBy(BpmResolution::source, Collectors.counting()));
        long resolved = counts.values().stream().mapToLong(Long::longValue).sum();
        return new BpmCoverageReport(
            counts.getOrDefault(BpmSource.MANUAL, 0L).intValue(),
            counts.getOrDefault(BpmSource.ACOUSTICBRAINZ, 0L).intValue(),
            counts.getOrDefault(BpmSource.DEEZER, 0L).intValue(),
            tracks.size() - (int) resolved);
    }

    private Optional<BpmResolution> fromManual(TrackCatalog track) {

        return manualMetricsRepository.findById(track.getSpotifyId())
            .map(ManualMetrics::getBpm)
            .map(bpm -> new BpmResolution(round(bpm), BpmSource.MANUAL));
    }

    private Optional<BpmResolution> fromAcousticBrainz(TrackCatalog track) {

        return audioFeaturesRepository.findByTrackSpotifyId(track.getSpotifyId())
            .map(AudioFeatures::getBpm)
            .map(bpm -> new BpmResolution(round(bpm), BpmSource.ACOUSTICBRAINZ));
    }

    private Optional<BpmResolution> fromDeezer(TrackCatalog track) {

        return Optional.ofNullable(track.getIsrc())
            .flatMap(deezerClient::findBpmByIsrc)
            .or(() -> searchFallback(track))
            .map(bpm -> new BpmResolution(round(bpm), BpmSource.DEEZER));
    }

    private Optional<BigDecimal> searchFallback(TrackCatalog track) {

        if (Objects.isNull(track.getArtist()) || Objects.isNull(track.getTitle())) {

            return Optional.empty();
        }
        return deezerClient.findBpmByArtistTitle(track.getArtist(), track.getTitle());
    }

    private BpmResolution withHalfTimeCorrection(TrackCatalog track, BpmResolution resolution) {

        int corrected = halfTimeCorrector.correct(track.getGenreFamily(), resolution.bpm());
        return corrected == resolution.bpm()
            ? resolution
            : new BpmResolution(corrected, resolution.source());
    }

    private int round(BigDecimal bpm) {

        return bpm.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }
}
