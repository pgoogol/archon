package com.pgoogol.finance.api;

import com.pgoogol.finance.recurring.application.OccurrenceGenerator;
import com.pgoogol.finance.recurring.application.RecurringRuleCommand;
import com.pgoogol.finance.recurring.application.RecurringRuleService;
import com.pgoogol.finance.recurring.domain.RecurringRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/finance/api/v1/recurring-rules")
@Tag(name = "recurring", description = "Rachunki cykliczne i terminarz płatności")
@RequiredArgsConstructor
public class RecurringRuleController {

    private final RecurringRuleService recurringRuleService;
    private final OccurrenceGenerator occurrenceGenerator;
    private final RecurringApiMapper mapper;

    @GetMapping
    @Operation(summary = "Reguły cykliczne, także wyłączone")
    public List<RecurringRuleResponse> listRecurringRules(
            @RequestParam(defaultValue = "false") boolean activeOnly) {

        List<RecurringRule> rules = recurringRuleService.list(activeOnly);
        return mapper.toResponses(rules);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Pojedyncza reguła")
    public RecurringRuleResponse getRecurringRule(@PathVariable long id) {

        RecurringRule rule = recurringRuleService.get(id);
        return mapper.toResponse(rule);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Nowa reguła cykliczna",
        description = """
            Zapisanie reguły od razu generuje pozycje terminarza na 12 miesięcy \
            w przód. Dzień miesiąca większy niż długość miesiąca przesuwa się na \
            jego ostatni dzień — rachunek z 31 dnia w lutym wypada 28 albo 29.""")
    public RecurringRuleResponse createRecurringRule(
            @Valid @RequestBody RecurringRuleRequest request) {

        RecurringRuleCommand command = mapper.toCommand(request);
        RecurringRule created = recurringRuleService.create(command);
        return mapper.toResponse(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Zmiana reguły",
        description = """
            Zmiana przelicza pozycje PENDING o terminie od dzisiaj. Pozycje PAID \
            i SKIPPED zostają nietknięte — historii nie przepisujemy.""")
    public RecurringRuleResponse updateRecurringRule(
            @PathVariable long id, @Valid @RequestBody RecurringRuleRequest request) {

        RecurringRuleCommand command = mapper.toCommand(request);
        RecurringRule updated = recurringRuleService.update(id, command);
        return mapper.toResponse(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Wyłączenie reguły",
        description = """
            Reguła przestaje generować terminarz, a jej pozycje PENDING \
            o terminie od dzisiaj znikają. Zapłacone i pominięte zostają — to \
            już historia.""")
    public void deactivateRecurringRule(@PathVariable long id) {

        recurringRuleService.deactivate(id);
    }

    @PostMapping("/generate")
    @Operation(summary = "Ręczne uzupełnienie terminarza",
        description = """
            To samo, co robi nocny przebieg. Wywołanie jest idempotentne — drugi \
            przebieg nie zmienia liczby pozycji.""")
    public GenerateOccurrencesResponse generateOccurrences() {

        OccurrenceGenerator.GenerationResult result = occurrenceGenerator.generateAll();
        return mapper.toResponse(result);
    }
}
