package com.pgoogol.finance.currency.application;

import com.pgoogol.finance.FinanceFixtures;
import com.pgoogol.finance.common.ConflictException;
import com.pgoogol.finance.common.NotFoundException;
import com.pgoogol.finance.currency.domain.Currency;
import com.pgoogol.finance.currency.domain.FinanceProperties;
import com.pgoogol.finance.currency.domain.MinorUnits;
import com.pgoogol.finance.currency.infrastructure.CurrencyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceTest {

    @Mock
    private CurrencyRepository currencyRepository;

    private final FinanceProperties financeProperties = new FinanceProperties(FinanceFixtures.PLN);

    private CurrencyService currencyService() {
        return new CurrencyService(currencyRepository, financeProperties);
    }

    @Test
    @DisplayName("kod waluty pisany małymi literami trafia w słownik")
    void get_whenCodeIsLowercase_normalizesBeforeLookup() {

        // given
        when(currencyRepository.findById(FinanceFixtures.EUR))
            .thenReturn(Optional.of(FinanceFixtures.euro()));

        // when
        Currency currency = currencyService().get("eur");

        // then
        assertThat(currency.getCode()).isEqualTo(FinanceFixtures.EUR);
    }

    @Test
    @DisplayName("waluta spoza słownika kończy się błędem, nie domyślną skalą")
    void get_whenCurrencyUnknown_fails() {

        // given
        when(currencyRepository.findById("XYZ")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> currencyService().get("XYZ"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("skala waluty pochodzi ze słownika, nie ze stałej")
    void minorUnitsOf_whenCurrencyHasNoFraction_returnsZeroScale() {

        // given
        when(currencyRepository.findById("JPY")).thenReturn(Optional.of(FinanceFixtures.yen()));

        // when
        MinorUnits units = currencyService().minorUnitsOf("JPY");

        // then
        assertThat(units.scale()).isZero();
    }

    @Test
    @DisplayName("dodanie waluty, która już jest w słowniku, jest konfliktem")
    void add_whenCurrencyExists_fails() {

        // given
        when(currencyRepository.existsById(FinanceFixtures.EUR)).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> currencyService().add("eur", "euro", 2))
            .isInstanceOf(ConflictException.class);
        verify(currencyRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("waluta bazowa rozpoznaje się po konfiguracji, nie po zapisie w kodzie")
    void isBase_whenCodeMatchesConfiguration_returnsTrue() {

        assertThat(currencyService().isBase("pln")).isTrue();
        assertThat(currencyService().isBase(FinanceFixtures.EUR)).isFalse();
    }
}
