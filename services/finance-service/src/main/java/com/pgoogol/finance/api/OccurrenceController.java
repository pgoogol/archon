package com.pgoogol.finance.api;

import com.pgoogol.finance.recurring.application.OccurrenceService;
import com.pgoogol.finance.recurring.domain.OccurrenceStatus;
import com.pgoogol.finance.recurring.domain.ScheduledOccurrence;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/finance/api/v1/occurrences")
@Tag(name = "recurring", description = "Rachunki cykliczne i terminarz płatności")
@RequiredArgsConstructor
public class OccurrenceController {

    private final OccurrenceService occurrenceService;
    private final RecurringApiMapper mapper;

    @GetMapping
    @Operation(summary = "Terminarz płatności",
        description = """
            Status OVERDUE nie jest przechowywany — to pozycja PENDING z terminem \
            wcześniejszym niż dziś, wyliczana przy odczycie. Przechowywany \
            wymagałby joba przepisującego statusy o północy.""")
    public List<OccurrenceResponse> listOccurrences(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) OccurrenceStatus status,
            @RequestParam(required = false) Long ruleId) {

        List<ScheduledOccurrence> found = occurrenceService.search(from, to, status, ruleId);
        LocalDate today = LocalDate.now();
        return mapper.toResponses(found, today);
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Oznaczenie pozycji jako zapłaconej",
        description = """
            Powstaje transakcja na faktyczną kwotę i datę, a pozycja terminarza \
            dostaje do niej odsyłacz. Kwota faktyczna bywa inna niż oczekiwana — \
            rachunek za prąd rzadko wychodzi co do grosza tak samo.""")
    public OccurrenceResponse payOccurrence(@PathVariable long id,
                                            @Valid @RequestBody PayOccurrenceRequest request) {

        ScheduledOccurrence paid = occurrenceService.pay(id, request.paidOn(),
            request.paidAmountMinor(), request.accountId());
        LocalDate today = LocalDate.now();
        return mapper.toResponse(paid, today);
    }

    @PostMapping("/{id}/skip")
    @Operation(summary = "Pominięcie pozycji terminarza",
        description = "Żadna transakcja nie powstaje — rachunek po prostu nie przyszedł.")
    public OccurrenceResponse skipOccurrence(@PathVariable long id) {

        ScheduledOccurrence skipped = occurrenceService.skip(id);
        LocalDate today = LocalDate.now();
        return mapper.toResponse(skipped, today);
    }
}
