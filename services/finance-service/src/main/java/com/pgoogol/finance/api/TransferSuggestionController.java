package com.pgoogol.finance.api;

import com.pgoogol.finance.currency.application.CurrencyService;
import com.pgoogol.finance.transaction.application.TransferSuggestionService;
import com.pgoogol.finance.transaction.domain.Transaction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/finance/api/v1/transfers")
@Tag(name = "categorization", description = "Reguły podpowiadające kategorię przy imporcie")
@RequiredArgsConstructor
public class TransferSuggestionController {

    private final TransferSuggestionService transferSuggestionService;
    private final CurrencyService currencyService;
    private final TransactionApiMapper mapper;

    @GetMapping("/candidates")
    @Operation(summary = "Pary wyglądające na dwie strony jednego przelewu",
        description = """
            Przelew między własnymi kontami przychodzi w dwóch wyciągach jako \
            zwykły wydatek i zwykły wpływ. Zostawiony tak, zawyża naraz wydatki \
            i przychody. Scalenie jest osobnym, potwierdzonym krokiem — \
            automatyczne zjadłoby dwie prawdziwe operacje o tej samej kwocie \
            w tym samym tygodniu.""")
    public List<TransferSuggestionService.TransferCandidate> listTransferCandidates(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return transferSuggestionService.candidates(from, to);
    }

    @PostMapping("/merge")
    @Operation(summary = "Scalenie potwierdzonej pary w jeden przelew",
        description = """
            Powstaje jedna transakcja typu TRANSFER, a obie strony znikają. \
            Warunki pary sprawdzamy jeszcze raz — między podglądem \
            a potwierdzeniem ktoś mógł zmienić kwotę albo datę.""")
    public TransactionResponse mergeTransfer(@Valid @RequestBody MergeTransferRequest request) {

        Transaction transfer = transferSuggestionService.merge(request.expenseTransactionId(),
            request.incomeTransactionId());
        String baseCurrency = currencyService.baseCurrency();
        return mapper.toResponse(transfer, baseCurrency);
    }
}
