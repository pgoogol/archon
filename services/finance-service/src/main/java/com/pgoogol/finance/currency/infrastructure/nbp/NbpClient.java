package com.pgoogol.finance.currency.infrastructure.nbp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pgoogol.finance.common.ErrorCodes;
import com.pgoogol.finance.common.ExceptionMessageConstants;
import com.pgoogol.finance.common.ExternalServiceException;
import com.pgoogol.finance.common.RateLimitedException;
import com.pgoogol.finance.common.ratelimit.ApiCallGuard;
import com.pgoogol.finance.currency.domain.ExchangeRateProvider;
import com.pgoogol.finance.currency.domain.FxRate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Kursy średnie z tabeli A NBP. Adapter portu {@link ExchangeRateProvider} —
 * poza tą klasą nic w module nie wie, że kursy pochodzą z NBP.
 *
 * <p>API zwraca 404 dla zakresu bez publikacji (weekend, święta, przyszłość).
 * To normalna sytuacja, nie awaria, więc kończy się pustą listą.</p>
 */
@Component
public class NbpClient implements ExchangeRateProvider {

    /**
     * Twardy limit NBP: pojedyncze zapytanie obejmuje najwyżej 93 dni.
     * Limit dostawcy pilnujemy w kodzie, nie w konfiguracji — dłuższy zakres
     * dzielimy na kawałki zamiast dostać błąd.
     */
    private static final int MAX_RANGE_DAYS = 93;

    private final RestClient restClient;
    private final ApiCallGuard guard;
    private final String table;

    public NbpClient(RestClient.Builder restClientBuilder, NbpProperties properties) {

        this.restClient = restClientBuilder.clone().baseUrl(properties.baseUrl()).build();
        this.guard = ApiCallGuard.of("nbp", properties.requestsPerSecond());
        this.table = properties.table();
    }

    @Override
    public List<FxRate> fetchRates(String code, LocalDate from, LocalDate to) {

        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        List<FxRate> collected = new ArrayList<>();
        chunks(from, to).forEach(chunk -> collected.addAll(fetchChunk(code, chunk)));
        return List.copyOf(collected);
    }

    /** Zakres dłuższy niż limit NBP dzielony na kolejne przedziały po 93 dni. */
    private Stream<DateRange> chunks(LocalDate from, LocalDate to) {

        long days = ChronoUnit.DAYS.between(from, to) + 1;
        long count = (days + MAX_RANGE_DAYS - 1) / MAX_RANGE_DAYS;
        return Stream.iterate(0L, index -> index + 1)
            .limit(Math.max(count, 1))
            .map(index -> {

                LocalDate start = from.plusDays(index * MAX_RANGE_DAYS);
                LocalDate end = start.plusDays(MAX_RANGE_DAYS - 1L);
                if (end.isAfter(to)) {

                    return new DateRange(start, to);
                }
                return new DateRange(start, end);
            });
    }

    private List<FxRate> fetchChunk(String code, DateRange range) {

        RatesResponse response = execute(() -> restClient.get()
            .uri("/api/exchangerates/rates/{table}/{code}/{from}/{to}/?format=json",
                table, code.toLowerCase(Locale.ROOT), range.from(), range.to())
            .retrieve()
            .body(RatesResponse.class));
        if (Objects.isNull(response) || Objects.isNull(response.rates())) {

            return List.of();
        }
        return response.rates().stream()
            .filter(rate -> Objects.nonNull(rate.mid()) && Objects.nonNull(rate.effectiveDate()))
            .map(rate -> new FxRate(rate.mid(), rate.effectiveDate()))
            .toList();
    }

    private <T> T execute(Supplier<T> call) {

        return guard.execute(() -> {

            try {

                return call.get();
            } catch (HttpClientErrorException.NotFound ex) {

                // brak tabeli w zakresie — dzień wolny, nie awaria
                return null;
            } catch (HttpClientErrorException.TooManyRequests ex) {

                throw new RateLimitedException(ErrorCodes.NBP_RATE_LIMITED,
                    ExceptionMessageConstants.NBP_RATE_LIMITED, null);
            } catch (HttpServerErrorException | ResourceAccessException ex) {

                throw new ExternalServiceException(ErrorCodes.NBP_UNAVAILABLE,
                    ExceptionMessageConstants.NBP_UNAVAILABLE, ex);
            }
        });
    }

    private record DateRange(LocalDate from, LocalDate to) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RatesResponse(String code, List<RateNode> rates) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RateNode(LocalDate effectiveDate, BigDecimal mid) {

    }
}
