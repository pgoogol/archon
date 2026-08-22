package com.pgoogol.finance.api;

import com.pgoogol.finance.recurring.application.OccurrenceGenerator;
import com.pgoogol.finance.recurring.application.RecurringRuleCommand;
import com.pgoogol.finance.recurring.domain.OccurrenceStatus;
import com.pgoogol.finance.recurring.domain.RecurringRule;
import com.pgoogol.finance.recurring.domain.ScheduledOccurrence;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDate;
import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface RecurringApiMapper {

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "accountName", source = "account.name")
    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    RecurringRuleResponse toResponse(RecurringRule rule);

    List<RecurringRuleResponse> toResponses(List<RecurringRule> rules);

    RecurringRuleCommand toCommand(RecurringRuleRequest request);

    GenerateOccurrencesResponse toResponse(OccurrenceGenerator.GenerationResult result);

    /**
     * Dzisiejsza data przychodzi parametrem, bo status {@code OVERDUE} nie jest
     * przechowywany — powstaje dopiero z porównania terminu z „dziś". Mapper
     * zostaje bezstanowy i nie sięga po zegar.
     */
    @Mapping(target = "ruleId", source = "occurrence.rule.id")
    @Mapping(target = "ruleName", source = "occurrence.rule.name")
    @Mapping(target = "accountId", source = "occurrence.rule.account.id")
    @Mapping(target = "categoryId", source = "occurrence.rule.category.id")
    @Mapping(target = "transactionId", source = "occurrence.transaction.id")
    @Mapping(target = "status", source = "status")
    OccurrenceResponse toResponse(ScheduledOccurrence occurrence, OccurrenceStatus status);

    default OccurrenceResponse toResponse(ScheduledOccurrence occurrence, LocalDate today) {

        OccurrenceStatus status = occurrence.statusOn(today);
        return toResponse(occurrence, status);
    }

    default List<OccurrenceResponse> toResponses(List<ScheduledOccurrence> occurrences,
                                                 LocalDate today) {

        return occurrences.stream()
            .map(occurrence -> toResponse(occurrence, today))
            .toList();
    }
}
