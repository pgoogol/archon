package com.pgoogol.finance.api;

import com.pgoogol.finance.currency.application.CurrencyService;
import com.pgoogol.finance.currency.domain.Currency;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/finance/currencies")
@Tag(name = "currencies", description = "Waluty i kursy wymiany")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyService currencyService;
    private final CurrencyApiMapper mapper;

    @GetMapping
    @Operation(summary = "Słownik walut wraz z liczbą miejsc po przecinku",
        description = """
            Klient formatuje kwoty według minorUnit z tego słownika — PLN ma 2, \
            JPY ma 0. Mnożenie przez 100 po stronie klienta jest błędem.""")
    public List<CurrencyResponse> listCurrencies() {

        List<Currency> currencies = currencyService.listAll();
        return mapper.toCurrencyResponses(currencies);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dodanie waluty do słownika")
    public CurrencyResponse addCurrency(@Valid @RequestBody CurrencyRequest request) {

        Currency currency = currencyService.add(
            request.code(), request.name(), request.minorUnit());
        return mapper.toResponse(currency);
    }
}
