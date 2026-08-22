package com.pgoogol.finance.recurring.application;

import com.pgoogol.finance.account.application.AccountService;
import com.pgoogol.finance.account.domain.Account;
import com.pgoogol.finance.category.application.CategoryService;
import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.category.domain.CategoryDirection;
import com.pgoogol.finance.common.ErrorCodes;
import com.pgoogol.finance.common.ExceptionMessageConstants;
import com.pgoogol.finance.common.NotFoundException;
import com.pgoogol.finance.common.ValidationException;
import com.pgoogol.finance.recurring.domain.RecurringRule;
import com.pgoogol.finance.recurring.infrastructure.RecurringRuleRepository;
import com.pgoogol.finance.recurring.infrastructure.ScheduledOccurrenceRepository;
import com.pgoogol.finance.transaction.domain.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reguły rachunków cyklicznych i utrzymanie ich terminarza.
 *
 * <p>Zmiana reguły dotyka wyłącznie pozycji <b>czekających o terminie od
 * dzisiaj</b>. Zapłacone i pominięte zostają takie, jakie były — historii nie
 * przepisujemy, nawet jeśli kwota rachunku właśnie się zmieniła.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RecurringRuleService {

    private final RecurringRuleRepository recurringRuleRepository;
    private final ScheduledOccurrenceRepository occurrenceRepository;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final OccurrenceGenerator occurrenceGenerator;

    public List<RecurringRule> list(boolean activeOnly) {

        return recurringRuleRepository.findAllDetailed(activeOnly);
    }

    public RecurringRule get(long id) {

        Optional<RecurringRule> rule = recurringRuleRepository.findDetailedById(id);
        return rule.orElseThrow(() -> new NotFoundException(ErrorCodes.RECURRING_RULE_NOT_FOUND,
            ExceptionMessageConstants.RECURRING_RULE_NOT_FOUND.formatted(id)));
    }

    @Transactional
    public RecurringRule create(RecurringRuleCommand command) {

        Account account = accountService.get(command.accountId());
        Category category = categoryService.get(command.categoryId());
        validate(command, category);

        RecurringRule rule = new RecurringRule(command.name(), account, category, command.type(),
            command.amountMinor(), account.getCurrency(), command.frequency(),
            command.dayOfMonth(), command.startsOn());
        rule.limitTo(command.endsOn());
        rule.describeMatching(command.matchPattern());
        RecurringRule saved = recurringRuleRepository.save(rule);

        // terminarz powstaje od razu — reguła bez pozycji nie pokazuje niczego
        // aż do nocnego przebiegu, a to wygląda jak zgubiony zapis
        occurrenceGenerator.generateFor(saved);
        return saved;
    }

    @Transactional
    public RecurringRule update(long id, RecurringRuleCommand command) {

        RecurringRule rule = get(id);
        Account account = accountService.get(command.accountId());
        Category category = categoryService.get(command.categoryId());
        validate(command, category);

        rule.redefine(command.name(), account, category, command.type(), command.amountMinor(),
            account.getCurrency(), command.frequency(), command.dayOfMonth(), command.startsOn(),
            command.endsOn(), command.matchPattern());
        recalculatePending(rule);
        return rule;
    }

    @Transactional
    public void deactivate(long id) {

        RecurringRule rule = get(id);
        rule.deactivate();
        LocalDate today = LocalDate.now();
        occurrenceRepository.deletePendingFrom(rule.getId(), today);
    }

    /**
     * Przelicza terminarz po zmianie reguły: przyszłe pozycje czekające znikają
     * i powstają od nowa według nowych dat i kwoty.
     *
     * <p>Kasowanie zamiast poprawiania w miejscu, bo zmiana częstotliwości albo
     * dnia miesiąca przesuwa same terminy — a wtedy nie ma czego poprawiać,
     * stara pozycja po prostu nie ma już odpowiednika.</p>
     *
     * <p>Zaległe pozycje czekające zostają nietknięte razem z zapłaconymi
     * i pominiętymi. Rachunek sprzed tygodnia przyszedł na kwotę sprzed
     * tygodnia — przepisanie jej dzisiejszą zmieniłoby to, co już się
     * wydarzyło.</p>
     */
    private void recalculatePending(RecurringRule rule) {

        LocalDate today = LocalDate.now();
        occurrenceRepository.deletePendingFrom(rule.getId(), today);
        if (!rule.isActive()) {

            return;
        }
        occurrenceGenerator.generateFor(rule);
    }

    private void validate(RecurringRuleCommand command, Category category) {

        if (Objects.equals(command.type(), TransactionType.TRANSFER)) {

            throw new ValidationException(ErrorCodes.RULE_TYPE_NOT_FLOW,
                ExceptionMessageConstants.RULE_TYPE_NOT_FLOW.formatted(command.type()));
        }
        if (command.amountMinor() <= 0) {

            throw new ValidationException(ErrorCodes.AMOUNT_NOT_POSITIVE,
                ExceptionMessageConstants.AMOUNT_NOT_POSITIVE);
        }
        CategoryDirection expected = expectedDirection(command.type());
        CategoryDirection actual = category.getDirection();
        if (!Objects.equals(actual, expected)) {

            throw new ValidationException(ErrorCodes.CATEGORY_DIRECTION_MISMATCH,
                ExceptionMessageConstants.CATEGORY_DIRECTION_MISMATCH.formatted(
                    category.getName(), actual, command.type()));
        }
    }

    private CategoryDirection expectedDirection(TransactionType type) {

        if (Objects.equals(type, TransactionType.INCOME)) {

            return CategoryDirection.INCOME;
        }
        return CategoryDirection.EXPENSE;
    }
}
