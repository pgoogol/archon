package com.pgoogol.music.api;

import com.pgoogol.music.catalog.CamelotKey;
import com.pgoogol.music.catalog.CatalogSearchCriteria;
import com.pgoogol.music.catalog.CatalogSearchCriteria.HarmonicFilter;
import com.pgoogol.music.catalog.CatalogSearchCriteria.LibraryFilter;
import com.pgoogol.music.catalog.CatalogSearchCriteria.MetricFilter;
import com.pgoogol.music.catalog.CatalogSearchCriteria.SoundFilter;
import com.pgoogol.music.catalog.CatalogSearchCriteria.TrackFilter;
import com.pgoogol.music.catalog.GenreFamily;
import com.pgoogol.music.catalog.TempoClass;
import com.pgoogol.music.common.ValidationException;
import com.pgoogol.music.playlist.SetCurve;
import com.pgoogol.music.playlist.SetFill;
import com.pgoogol.music.playlist.SetProposal;
import com.pgoogol.music.playlist.SetProposalService;
import com.pgoogol.music.playlist.SetSuggestions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Objects;

@RestController
@RequestMapping("/music/api/v1/sets")
@Tag(name = "Sets", description = "Generator setu i domykanie gotowego setu")
@RequiredArgsConstructor
public class SetController {

    private final SetProposalService setProposalService;
    private final CatalogApiMapper catalogApiMapper;

    @PostMapping("/propose")
    @Operation(summary = "Propozycja setu na zadany czas",
        description = """
            Układa set z utworów spełniających te same filtry co wyszukiwarka: \
            kształt wieczoru wg profilu curve (STANDARD 25/30/30/15, WEDDING 30/30/25/15, \
            CLUB 15/25/45/15, EVEN 25/25/25/25 — udziały faz wieczoru), \
            utwór raz w secie, ten sam wykonawca nie częściej niż raz na 30 minut, \
            kary za skok BPM, zderzenie tonacji i brak oceny. \
            NICZEGO NIE ZAPISUJE — playlistę zakłada DJ przez /music/api/v1/playlists. \
            Ten sam seed daje tę samą propozycję.""")
    public SetProposalResponse propose(@Valid @RequestBody SetProposalRequest request) {

        SetProposal proposal = setProposalService.propose(
            criteria(request), request.targetMinutes(), curve(request.curve()), request.seed());
        return new SetProposalResponse(
            proposal.tracks().size(),
            proposal.totalDurationMs(),
            proposal.targetDurationMs(),
            proposal.seed(),
            proposal.notes(),
            proposal.tracks().stream().map(this::toResponse).toList());
    }

    @PostMapping("/{playlistId}/fill")
    @Operation(summary = "Uzupełnij gotowy set do zadanego czasu",
        description = """
            Dokłada dalszy ciąg do setu, który już stoi: utwory z setu zajmują \
            początek wieczoru (liczą się do czasu, blokują powtórkę utworu i odstęp \
            wykonawcy), a wynikiem jest sama końcówka. targetMinutes to długość CAŁEGO \
            wieczoru, nie tego, co dochodzi. NICZEGO NIE ZAPISUJE — utwory dopisuje \
            DJ przez /music/api/v1/playlists/{id}/tracks. Ten sam seed daje ten sam dalszy ciąg.""")
    public SetFillResponse fill(@PathVariable Long playlistId,
                                @Valid @RequestBody SetFillRequest request) {

        SetFill fill = setProposalService.fill(
            playlistId, criteria(request), request.targetMinutes(), curve(request.curve()),
            request.seed());
        SetProposal proposal = fill.proposal();
        return new SetFillResponse(
            fill.currentTrackCount(),
            fill.currentDurationMs(),
            proposal.tracks().size(),
            proposal.totalDurationMs(),
            proposal.targetDurationMs(),
            proposal.seed(),
            proposal.notes(),
            proposal.tracks().stream().map(this::toResponse).toList());
    }

    @PostMapping("/{playlistId}/suggest")
    @Operation(summary = "Dobierz utwór na wskazane miejsce w secie",
        description = """
            Kandydaci na jedną lukę w gotowym secie, uszeregowani od najlepiej \
            pasującego: kara za przejście liczona od utworu przed luką i do utworu za nią, \
            utwór już w secie odpada, wykonawca nie wraca przed upływem 30 minut. \
            Bez losowania — ta sama luka daje tę samą odpowiedź. \
            position: 0 przed pierwszym utworem, brak wartości = na koniec.""")
    public SetSuggestionResponse suggest(@PathVariable Long playlistId,
                                         @Valid @RequestBody SetSuggestionRequest request) {

        SetSuggestions suggestions = setProposalService.suggest(
            playlistId, criteria(request), request.position(), request.limit());
        return new SetSuggestionResponse(
            suggestions.position(),
            suggestions.suggestions().stream()
                .map(suggestion -> new SetSuggestionResponse.SuggestedTrackResponse(
                    Objects.toString(suggestion.djSlot(), null),
                    suggestion.bpmDelta(),
                    suggestion.harmonic(),
                    catalogApiMapper.toResponse(suggestion.track())))
                .toList());
    }

    /** Brak profilu w żądaniu to najczęstszy przypadek — domyślny przebieg. */
    private SetCurve curve(String raw) {

        if (Objects.isNull(raw) || raw.isBlank()) {

            return SetCurve.STANDARD;
        }
        return SetCurve.parse(raw).orElseThrow(() -> new ValidationException("INVALID_SET_CURVE",
            "Nieprawidłowy profil wieczoru: '%s' (oczekiwano STANDARD, WEDDING, CLUB albo EVEN)"
                .formatted(raw)));
    }

    private SetProposalResponse.ProposedTrackResponse toResponse(SetProposal.ProposedTrack track) {

        return new SetProposalResponse.ProposedTrackResponse(
            track.position(),
            Objects.toString(track.djSlot(), null),
            catalogApiMapper.toResponse(track.track()));
    }

    private CatalogSearchCriteria criteria(SetFilters request) {

        return new CatalogSearchCriteria(
            request.search(),
            new TrackFilter(parseEnum(GenreFamily.class, request.genreFamily(), "genreFamily"),
                null, null, null, null, null, null),
            new SoundFilter(request.bpmMin(), request.bpmMax(),
                parseEnum(TempoClass.class, request.tempoClass(), "tempoClass"),
                request.energy(), harmonicFilter(request)),
            new LibraryFilter(request.inLibrary(), request.ratingMin(), request.tag()),
            new MetricFilter(request.valenceMin(), request.valenceMax(),
                request.instrumentalMin(), request.livenessMax()),
            null);
    }

    private HarmonicFilter harmonicFilter(SetFilters request) {

        if (Objects.isNull(request.camelot()) || request.camelot().isBlank()) {

            return null;
        }
        return CamelotKey.ofLabel(request.camelot())
            .map(key -> new HarmonicFilter(key,
                !Boolean.FALSE.equals(request.camelotCompatible())))
            .orElseThrow(() -> new ValidationException("INVALID_CAMELOT",
                "Nieprawidłowa pozycja koła Camelot: '%s' (oczekiwano 1A–12B)"
                    .formatted(request.camelot())));
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {

        if (Objects.isNull(raw) || raw.isBlank()) {

            return null;
        }
        try {

            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {

            throw new ValidationException("INVALID_PARAMETER",
                "Nieprawidłowa wartość '%s' dla pola %s".formatted(raw, field));
        }
    }
}
