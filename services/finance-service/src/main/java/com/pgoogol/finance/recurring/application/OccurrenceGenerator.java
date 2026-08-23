package com.pgoogol.finance.recurring.application;

import com.pgoogol.finance.recurring.domain.RecurringRule;
import com.pgoogol.finance.recurring.domain.ScheduledOccurrence;
import com.pgoogol.finance.recurring.infrastructure.RecurringRuleRepository;
import com.pgoogol.finance.recurring.infrastructure.ScheduledOccurrenceRepository;
import com.pgoogol.finance.recurring.schedule.OccurrenceSchedule;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Rozwija reguły cykliczne na konkretne terminy.
 *
 * <p>Przebieg jest <b>idempotentny</b>: dokłada wyłącznie terminy, których
 * reguła jeszcze nie ma. Pilnują tego dwie rzeczy naraz — porównanie z datami
 * już zapisanymi i ograniczenie {@code ux_occurrence (rule_id, due_date)}
 * w bazie. Drugi przebieg tego samego dnia nie zmienia liczby pozycji.</p>
 *
 * <p>Okno sięga {@value #HORIZON_MONTHS} miesięcy w przód od dziś, a wstecz do
 * {@code startsOn} reguły — reguła założona z datą początku w przeszłości ma od
 * razu pokazać zaległości, a nie milczeć o nich do najbliższego terminu.</p>
 */
@Component
@RequiredArgsConstructor
public class OccurrenceGenerator {

    static final int HORIZON_MONTHS = 12;

    private static final Logger log = LoggerFactory.getLogger(OccurrenceGenerator.class);

    private final RecurringRuleRepository recurringRuleRepository;
    private final ScheduledOccurrenceRepository occurrenceRepository;
    private final OccurrenceSchedule occurrenceSchedule;

    /**
     * Nocne uzupełnienie terminarza. Robi dokładnie to samo, co wywołanie
     * z API — nieudany przebieg nie zostawia niczego, czego nie da się nadrobić
     * ręcznie.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void generateNightly() {

        GenerationResult result = generateAll();
        log.info("Nocne uzupełnienie terminarza: dołożono {} pozycji, horyzont do {}",
            result.createdCount(), result.horizonTo());
    }

    @Transactional
    public GenerationResult generateAll() {

        LocalDate horizon = horizon();
        List<RecurringRule> active = recurringRuleRepository.findActive();
        int created = active.stream()
            .mapToInt(rule -> generate(rule, horizon))
            .sum();
        return new GenerationResult(created, horizon);
    }

    /** Uzupełnienie terminarza jednej reguły — po jej zapisaniu albo zmianie. */
    @Transactional
    public int generateFor(RecurringRule rule) {

        LocalDate horizon = horizon();
        return generate(rule, horizon);
    }

    private int generate(RecurringRule rule, LocalDate horizon) {

        List<LocalDate> dueDates = occurrenceSchedule.dueDatesBetween(rule.getStartsOn(),
            rule.getEndsOn(), rule.getFrequency(), rule.getDayOfMonth(), rule.getStartsOn(),
            horizon);
        List<LocalDate> known = occurrenceRepository.findDueDates(rule.getId(), horizon);
        Set<LocalDate> alreadyScheduled = Set.copyOf(known);
        List<ScheduledOccurrence> fresh = dueDates.stream()
            .filter(dueDate -> !alreadyScheduled.contains(dueDate))
            .map(dueDate -> occurrence(rule, dueDate))
            .toList();
        if (fresh.isEmpty()) {

            return 0;
        }
        occurrenceRepository.saveAll(fresh);
        return fresh.size();
    }

    private ScheduledOccurrence occurrence(RecurringRule rule, LocalDate dueDate) {

        return new ScheduledOccurrence(rule, dueDate, rule.getAmountMinor(), rule.getCurrency());
    }

    private LocalDate horizon() {

        LocalDate today = LocalDate.now();
        return today.plusMonths(HORIZON_MONTHS);
    }

    /**
     * @param createdCount ile pozycji dołożono; drugi przebieg daje zero
     * @param horizonTo    do kiedy sięga wygenerowany terminarz
     */
    public record GenerationResult(int createdCount, LocalDate horizonTo) {

    }
}
