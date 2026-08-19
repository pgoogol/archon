package com.pgoogol.finance.currency.application;

import com.pgoogol.finance.FinanceFixtures;
import com.pgoogol.finance.common.NotFoundException;
import com.pgoogol.finance.common.ValidationException;
import com.pgoogol.finance.currency.domain.Currency;
import com.pgoogol.finance.currency.domain.ExchangeRate;
import com.pgoogol.finance.currency.domain.ExchangeRateProvider;
import com.pgoogol.finance.currency.domain.FxRate;
import com.pgoogol.finance.currency.domain.RateSource;
import com.pgoogol.finance.currency.infrastructure.ExchangeRateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 8, 14);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 8, 15);

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private CurrencyService currencyService;

    @Mock
    private ExchangeRateProvider exchangeRateProvider;

    @InjectMocks
    private ExchangeRateService exchangeRateService;

    @Test
    @DisplayName("transakcja z soboty dostaje kurs z piątku, a data kursu to piątek")
    void resolve_whenNoRateOnRequestedDay_usesLatestEarlierRate() {

        // given
        Currency euro = FinanceFixtures.euro();
        when(currencyService.get(FinanceFixtures.EUR)).thenReturn(euro);
        when(currencyService.isBase(FinanceFixtures.EUR)).thenReturn(false);
        when(exchangeRateRepository
            .findTopByIdCodeAndIdRateDateLessThanEqualOrderByIdRateDateDesc(
                FinanceFixtures.EUR, SATURDAY))
            .thenReturn(Optional.of(FinanceFixtures.rate(FinanceFixtures.EUR, FRIDAY, "4.3215")));

        // when
        FxRate rate = exchangeRateService.resolve(FinanceFixtures.EUR, SATURDAY);

        // then
        assertThat(rate.rate()).isEqualByComparingTo("4.3215");
        assertThat(rate.rateDate()).isEqualTo(FRIDAY);
    }

    @Test
    @DisplayName("waluta bazowa ma kurs jeden bez sięgania do tabeli kursów")
    void resolve_whenCurrencyIsBase_returnsIdentityWithoutRepository() {

        // given
        Currency zloty = FinanceFixtures.zloty();
        when(currencyService.get(FinanceFixtures.PLN)).thenReturn(zloty);
        when(currencyService.isBase(FinanceFixtures.PLN)).thenReturn(true);

        // when
        FxRate rate = exchangeRateService.resolve(FinanceFixtures.PLN, SATURDAY);

        // then
        assertThat(rate.rate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(rate.rateDate()).isEqualTo(SATURDAY);
        verify(exchangeRateRepository, never())
            .findTopByIdCodeAndIdRateDateLessThanEqualOrderByIdRateDateDesc(any(), any());
    }

    @Test
    @DisplayName("brak jakiegokolwiek wcześniejszego kursu kończy się błędem, nie kursem zastępczym")
    void resolve_whenNoRateAtAll_fails() {

        // given
        when(currencyService.get(FinanceFixtures.EUR)).thenReturn(FinanceFixtures.euro());
        when(currencyService.isBase(FinanceFixtures.EUR)).thenReturn(false);
        when(exchangeRateRepository
            .findTopByIdCodeAndIdRateDateLessThanEqualOrderByIdRateDateDesc(
                FinanceFixtures.EUR, SATURDAY))
            .thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> exchangeRateService.resolve(FinanceFixtures.EUR, SATURDAY))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Brak kursu");
    }

    @Test
    @DisplayName("wpis ręczny waluty bazowej jest odrzucany — jej kurs z definicji wynosi jeden")
    void saveManual_whenCurrencyIsBase_fails() {

        // given
        when(currencyService.get(FinanceFixtures.PLN)).thenReturn(FinanceFixtures.zloty());
        when(currencyService.isBase(FinanceFixtures.PLN)).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> exchangeRateService.saveManual(
            FinanceFixtures.PLN, FRIDAY, new BigDecimal("1.5")))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("wpis ręczny zapisuje się ze źródłem MANUAL")
    void saveManual_whenCurrencyIsForeign_savesWithManualSource() {

        // given
        when(currencyService.get(FinanceFixtures.EUR)).thenReturn(FinanceFixtures.euro());
        when(currencyService.isBase(FinanceFixtures.EUR)).thenReturn(false);
        when(exchangeRateRepository.findById(any())).thenReturn(Optional.empty());
        when(exchangeRateRepository.save(any(ExchangeRate.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        exchangeRateService.saveManual(FinanceFixtures.EUR, FRIDAY, new BigDecimal("4.5"));

        // then
        ArgumentCaptor<ExchangeRate> saved = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(exchangeRateRepository).save(saved.capture());
        assertThat(saved.getValue().getSource()).isEqualTo(RateSource.MANUAL);
        assertThat(saved.getValue().getRateDate()).isEqualTo(FRIDAY);
    }

    @Test
    @DisplayName("zakres dat od późniejszej do wcześniejszej jest odrzucany")
    void sync_whenRangeReversed_fails() {

        assertThatThrownBy(() -> exchangeRateService.sync(SATURDAY, FRIDAY, List.of()))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("synchronizacja bez podanych walut pomija bazową")
    void sync_whenNoCodesGiven_skipsBaseCurrency() {

        // given
        when(currencyService.listAll())
            .thenReturn(List.of(FinanceFixtures.zloty(), FinanceFixtures.euro()));
        when(currencyService.isBase(FinanceFixtures.PLN)).thenReturn(true);
        when(currencyService.isBase(FinanceFixtures.EUR)).thenReturn(false);
        when(exchangeRateProvider.fetchRates(eq(FinanceFixtures.EUR), any(), any()))
            .thenReturn(List.of(new FxRate(new BigDecimal("4.3215"), FRIDAY)));
        when(exchangeRateRepository.findById(any())).thenReturn(Optional.empty());
        when(exchangeRateRepository.save(any(ExchangeRate.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ExchangeRateService.SyncResult result =
            exchangeRateService.sync(FRIDAY, SATURDAY, List.of());

        // then
        assertThat(result.codes()).containsExactly(FinanceFixtures.EUR);
        assertThat(result.savedCount()).isEqualTo(1);
        verify(exchangeRateProvider, never()).fetchRates(eq(FinanceFixtures.PLN), any(), any());
    }

    @Test
    @DisplayName("ponowna synchronizacja tego samego dnia nadpisuje kurs zamiast dublować wpis")
    void sync_whenRateAlreadyStored_replacesInsteadOfInserting() {

        // given
        ExchangeRate existing = FinanceFixtures.rate(FinanceFixtures.EUR, FRIDAY, "4.0000");
        when(currencyService.get(FinanceFixtures.EUR)).thenReturn(FinanceFixtures.euro());
        when(currencyService.isBase(FinanceFixtures.EUR)).thenReturn(false);
        when(exchangeRateProvider.fetchRates(eq(FinanceFixtures.EUR), any(), any()))
            .thenReturn(List.of(new FxRate(new BigDecimal("4.3215"), FRIDAY)));
        when(exchangeRateRepository.findById(any())).thenReturn(Optional.of(existing));

        // when
        exchangeRateService.sync(FRIDAY, FRIDAY, List.of(FinanceFixtures.EUR));

        // then
        assertThat(existing.getRate()).isEqualByComparingTo("4.3215");
        assertThat(existing.getSource()).isEqualTo(RateSource.NBP);
        verify(exchangeRateRepository, never()).save(any(ExchangeRate.class));
    }
}
