package com.pgoogol.finance.currency.infrastructure.nbp;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.pgoogol.finance.WireMockRestClients;
import com.pgoogol.finance.common.ExternalServiceException;
import com.pgoogol.finance.currency.domain.FxRate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class NbpClientTest {

    private static final String RATES_PATH = "/api/exchangerates/rates/a/eur/.*";

    private NbpClient client(WireMockRuntimeInfo wireMock) {

        return new NbpClient(WireMockRestClients.builder(),
            new NbpProperties(wireMock.getHttpBaseUrl(), 50, "a"));
    }

    @Test
    @DisplayName("kursy z tabeli A trafiają do modelu razem z datą publikacji")
    void fetchRates_whenTableAvailable_mapsRatesWithEffectiveDates(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(get(urlPathMatching(RATES_PATH)).willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {"table":"A","currency":"euro","code":"EUR","rates":[
                  {"no":"157/A/NBP/2026","effectiveDate":"2026-08-14","mid":4.3215},
                  {"no":"158/A/NBP/2026","effectiveDate":"2026-08-18","mid":4.3301}
                ]}""")));

        // when
        List<FxRate> rates = client(wireMock).fetchRates("EUR",
            LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 18));

        // then
        assertThat(rates).hasSize(2);
        assertThat(rates.getFirst().rate()).isEqualByComparingTo("4.3215");
        assertThat(rates.getFirst().rateDate()).isEqualTo(LocalDate.of(2026, 8, 14));
    }

    @Test
    @DisplayName("brak tabeli w zakresie to dzień wolny, nie awaria — pusta lista")
    void fetchRates_whenNoTablePublished_returnsEmptyList(WireMockRuntimeInfo wireMock) {

        // given: NBP odpowiada 404 na weekend i święta
        stubFor(get(urlPathMatching(RATES_PATH)).willReturn(aResponse().withStatus(404)));

        // when
        List<FxRate> rates = client(wireMock).fetchRates("EUR",
            LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 16));

        // then
        assertThat(rates).isEmpty();
    }

    @Test
    @DisplayName("zakres dłuższy niż limit NBP dzieli się na kilka zapytań")
    void fetchRates_whenRangeExceedsProviderLimit_splitsIntoChunks(WireMockRuntimeInfo wireMock) {

        // given: 120 dni to więcej niż dozwolone 93 w jednym zapytaniu
        stubFor(get(urlPathMatching(RATES_PATH)).willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {"table":"A","currency":"euro","code":"EUR","rates":[
                  {"no":"1/A/NBP/2026","effectiveDate":"2026-01-02","mid":4.1000}
                ]}""")));

        // when
        List<FxRate> rates = client(wireMock).fetchRates("EUR",
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));

        // then
        verify(2, getRequestedFor(urlPathMatching(RATES_PATH)));
        assertThat(rates).hasSize(2);
    }

    @Test
    @DisplayName("awaria po stronie NBP kończy się typowanym błędem, nie surowym wyjątkiem HTTP")
    void fetchRates_whenProviderFails_throwsExternalServiceException(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(get(urlPathMatching(RATES_PATH)).willReturn(aResponse().withStatus(500)));

        // when / then
        assertThatThrownBy(() -> client(wireMock).fetchRates("EUR",
            LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 14)))
            .isInstanceOf(ExternalServiceException.class)
            .hasMessageContaining("NBP");
    }
}
