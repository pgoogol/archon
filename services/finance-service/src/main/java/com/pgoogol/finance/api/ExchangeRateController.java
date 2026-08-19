package com.pgoogol.finance.api;

import com.pgoogol.finance.currency.application.ExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/finance/exchange-rates")
@Tag(name = "currencies", description = "Waluty i kursy wymiany")
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;
    private final CurrencyApiMapper mapper;

    public ExchangeRateController(ExchangeRateService exchangeRateService,
                                  CurrencyApiMapper mapper) {

        this.exchangeRateService = exchangeRateService;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Kursy waluty w zakresie dat")
    public List<ExchangeRateResponse> listExchangeRates(
            @RequestParam String code,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return mapper.toRateResponses(exchangeRateService.list(code, from, to));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ręczny wpis kursu",
        description = """
            Zapisany kurs dostaje źródło MANUAL. Ponowny wpis na tę samą datę \
            nadpisuje poprzedni. Kurs podaje się jako tekst dziesiętny — \
            liczba zmiennoprzecinkowa gubi grosze.""")
    public ExchangeRateResponse addExchangeRate(@Valid @RequestBody ExchangeRateRequest request) {

        return mapper.toResponse(exchangeRateService.saveManual(
            request.code(), request.rateDate(), new BigDecimal(request.rate())));
    }

    @PostMapping("/sync")
    @Operation(summary = "Pobranie kursów z NBP za zakres dat",
        description = """
            Tabela A NBP. Waluta bazowa jest pomijana — jej kurs z definicji \
            wynosi 1. Dni bez publikacji (weekendy, święta) po prostu nie mają \
            wpisu i nie są błędem.""")
    public SyncExchangeRatesResponse syncExchangeRates(
            @Valid @RequestBody SyncExchangeRatesRequest request) {

        return mapper.toResponse(exchangeRateService.sync(
            request.from(), request.to(), request.codes()));
    }
}
