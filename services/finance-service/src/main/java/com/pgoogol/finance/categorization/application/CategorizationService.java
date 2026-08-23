package com.pgoogol.finance.categorization.application;

import com.pgoogol.finance.categorization.domain.CategoryRule;
import com.pgoogol.finance.categorization.domain.MatchField;
import com.pgoogol.finance.categorization.infrastructure.CategoryRuleRepository;
import com.pgoogol.finance.categorization.match.RowFacts;
import com.pgoogol.finance.categorization.match.TextMatcher;
import com.pgoogol.finance.category.application.CategoryService;
import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.common.ErrorCodes;
import com.pgoogol.finance.common.ExceptionMessageConstants;
import com.pgoogol.finance.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reguły podpowiadające kategorię i ich stosowanie.
 *
 * <p>Reguła nigdy nie przypisuje kategorii — wypełnia pole <b>sugestii</b>,
 * które użytkownik potwierdza w podglądzie wyciągu. Reguła napisana zbyt
 * szeroko ma w najgorszym razie zaproponować bzdurę, a nie zapisać jej.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CategorizationService {

    private static final Logger log = LoggerFactory.getLogger(CategorizationService.class);

    private final CategoryRuleRepository categoryRuleRepository;
    private final CategoryService categoryService;
    private final TextMatcher textMatcher;

    public List<CategoryRule> list(boolean activeOnly) {

        return categoryRuleRepository.findAllOrdered(activeOnly);
    }

    public CategoryRule get(long id) {

        Optional<CategoryRule> rule = categoryRuleRepository.findDetailedById(id);
        return rule.orElseThrow(() -> new NotFoundException(ErrorCodes.CATEGORY_RULE_NOT_FOUND,
            ExceptionMessageConstants.CATEGORY_RULE_NOT_FOUND.formatted(id)));
    }

    @Transactional
    public CategoryRule create(String pattern, MatchField matchField, long categoryId,
                               int priority) {

        Category category = categoryService.get(categoryId);
        CategoryRule rule = new CategoryRule(pattern, matchField, category, priority);
        return categoryRuleRepository.save(rule);
    }

    @Transactional
    public CategoryRule update(long id, String pattern, MatchField matchField, long categoryId,
                               int priority, boolean active) {

        CategoryRule rule = get(id);
        Category category = categoryService.get(categoryId);
        rule.redefine(pattern, matchField, category, priority, active);
        return rule;
    }

    @Transactional
    public void delete(long id) {

        CategoryRule rule = get(id);
        categoryRuleRepository.delete(rule);
    }

    /**
     * Reguła powstała z poprawki użytkownika w podglądzie. Zapisujemy ją tylko
     * wtedy, gdy takiej jeszcze nie ma — poprawianie tego samego kontrahenta co
     * miesiąc nie może rozmnażać reguł.
     */
    @Transactional
    public Optional<CategoryRule> rememberCorrection(String pattern, MatchField matchField,
                                                     long categoryId, int priority) {

        if (StringUtils.isBlank(pattern)) {

            return Optional.empty();
        }
        String trimmed = pattern.trim();
        boolean known =
            categoryRuleRepository.existsByPatternIgnoreCaseAndCategoryId(trimmed, categoryId);
        if (known) {

            return Optional.empty();
        }
        CategoryRule created = create(trimmed, matchField, categoryId, priority);
        log.info("Zapamiętano regułę kategoryzacji '{}' dla kategorii {}", trimmed, categoryId);
        return Optional.of(created);
    }

    /**
     * Pierwsza pasująca reguła w kolejności priorytetu. Reguły przekazujemy
     * z zewnątrz, bo podgląd wyciągu dopasowuje setki wierszy naraz — wczytanie
     * ich raz na wiersz byłoby zapytaniem na wiersz.
     */
    public Optional<Category> suggestFor(RowFacts row, List<CategoryRule> rules) {

        Objects.requireNonNull(row, "row");
        return rules.stream()
            .filter(rule -> matches(row, rule))
            .findFirst()
            .map(CategoryRule::getCategory);
    }

    private boolean matches(RowFacts row, CategoryRule rule) {

        MatchField field = rule.getMatchField();
        String pattern = rule.getPattern();
        if (Objects.equals(field, MatchField.DESCRIPTION)) {

            return textMatcher.contains(row.description(), pattern);
        }
        if (Objects.equals(field, MatchField.COUNTERPARTY)) {

            return textMatcher.contains(row.counterparty(), pattern);
        }
        boolean inDescription = textMatcher.contains(row.description(), pattern);
        if (inDescription) {

            return true;
        }
        return textMatcher.contains(row.counterparty(), pattern);
    }
}
