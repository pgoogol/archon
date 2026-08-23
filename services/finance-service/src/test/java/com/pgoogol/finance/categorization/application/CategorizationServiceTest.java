package com.pgoogol.finance.categorization.application;

import com.pgoogol.finance.FinanceFixtures;
import com.pgoogol.finance.categorization.domain.CategoryRule;
import com.pgoogol.finance.categorization.domain.MatchField;
import com.pgoogol.finance.categorization.infrastructure.CategoryRuleRepository;
import com.pgoogol.finance.categorization.match.RowFacts;
import com.pgoogol.finance.categorization.match.TextMatcher;
import com.pgoogol.finance.category.application.CategoryService;
import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.category.domain.CategoryDirection;
import com.pgoogol.finance.common.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategorizationServiceTest {

    @Mock
    private CategoryRuleRepository categoryRuleRepository;

    @Mock
    private CategoryService categoryService;

    @Spy
    private TextMatcher textMatcher = new TextMatcher();

    @InjectMocks
    private CategorizationService service;

    @Test
    @DisplayName("suggestFor gdy pasuje kilka reguł, bierze pierwszą w kolejności")
    void suggestFor_whenSeveralRulesMatch_takesTheFirstInOrder() {

        // given: kolejność ustala repozytorium priorytetem, serwis jej nie zmienia
        Category food = FinanceFixtures.category(1L, "Jedzenie", CategoryDirection.EXPENSE);
        Category shops = FinanceFixtures.category(2L, "Sklepy", CategoryDirection.EXPENSE);
        List<CategoryRule> rules = List.of(
            new CategoryRule("biedronka", MatchField.ANY, food, 10),
            new CategoryRule("zakup", MatchField.ANY, shops, 20));
        RowFacts row = row("ZAKUP BIEDRONKA 1234", null);

        // when
        Optional<Category> suggested = service.suggestFor(row, rules);

        // then
        assertThat(suggested).contains(food);
    }

    @Test
    @DisplayName("suggestFor gdy reguła patrzy na kontrahenta, ignoruje opis")
    void suggestFor_whenRuleLooksAtCounterparty_ignoresDescription() {

        // given
        Category media = FinanceFixtures.category(3L, "Media", CategoryDirection.EXPENSE);
        List<CategoryRule> rules =
            List.of(new CategoryRule("tauron", MatchField.COUNTERPARTY, media, 10));

        // when: słowo siedzi w opisie, nie u kontrahenta
        Optional<Category> suggested = service.suggestFor(row("PRZELEW TAURON", "Sklep"), rules);

        // then
        assertThat(suggested).isEmpty();
    }

    @Test
    @DisplayName("suggestFor gdy nic nie pasuje, nie podpowiada niczego")
    void suggestFor_whenNothingMatches_suggestsNothing() {

        // given
        Category media = FinanceFixtures.category(3L, "Media", CategoryDirection.EXPENSE);
        List<CategoryRule> rules = List.of(new CategoryRule("prad", MatchField.ANY, media, 10));

        // when
        Optional<Category> suggested = service.suggestFor(row("ZAKUP LIDL", null), rules);

        // then
        assertThat(suggested).isEmpty();
    }

    @Test
    @DisplayName("rememberCorrection gdy taka reguła już jest, nie tworzy drugiej")
    void rememberCorrection_whenRuleAlreadyExists_createsNoDuplicate() {

        // given: poprawianie tego samego kontrahenta co miesiąc nie może
        // rozmnażać reguł
        when(categoryRuleRepository.existsByPatternIgnoreCaseAndCategoryId("Biedronka", 1L))
            .thenReturn(true);

        // when
        Optional<CategoryRule> created =
            service.rememberCorrection("  Biedronka  ", MatchField.ANY, 1L, 200);

        // then
        assertThat(created).isEmpty();
        verify(categoryRuleRepository, never()).save(any());
    }

    @Test
    @DisplayName("rememberCorrection gdy wzorzec jest pusty, nie zapisuje reguły")
    void rememberCorrection_whenPatternIsBlank_savesNothing() {

        // given & when
        Optional<CategoryRule> created =
            service.rememberCorrection("   ", MatchField.ANY, 1L, 200);

        // then
        assertThat(created).isEmpty();
        verify(categoryRuleRepository, never()).existsByPatternIgnoreCaseAndCategoryId(anyString(),
            anyLong());
    }

    @Test
    @DisplayName("rememberCorrection gdy reguły nie ma, zapisuje ją z przyciętym wzorcem")
    void rememberCorrection_whenRuleIsNew_savesItWithTrimmedPattern() {

        // given
        Category food = FinanceFixtures.category(1L, "Jedzenie", CategoryDirection.EXPENSE);
        when(categoryRuleRepository.existsByPatternIgnoreCaseAndCategoryId("Biedronka", 1L))
            .thenReturn(false);
        when(categoryService.get(1L)).thenReturn(food);
        when(categoryRuleRepository.save(any(CategoryRule.class)))
            .thenAnswer(call -> call.getArgument(0));

        // when
        Optional<CategoryRule> created =
            service.rememberCorrection("  Biedronka  ", MatchField.ANY, 1L, 200);

        // then
        assertThat(created).isPresent();
        assertThat(created.get().getPattern()).isEqualTo("Biedronka");
    }

    @Test
    @DisplayName("get gdy reguły nie ma, zgłasza brak")
    void get_whenRuleIsMissing_reportsNotFound() {

        // given
        when(categoryRuleRepository.findDetailedById(404L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(NotFoundException.class);
    }

    private RowFacts row(String description, String counterparty) {

        return new RowFacts(LocalDate.of(2026, 3, 10), -1_000L, "PLN", description, counterparty);
    }
}
