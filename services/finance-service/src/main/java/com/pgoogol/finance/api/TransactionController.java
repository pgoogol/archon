package com.pgoogol.finance.api;

import com.pgoogol.finance.currency.application.CurrencyService;
import com.pgoogol.finance.transaction.application.TransactionCommand;
import com.pgoogol.finance.transaction.application.TransactionSearchCriteria;
import com.pgoogol.finance.transaction.application.TransactionService;
import com.pgoogol.finance.transaction.domain.Transaction;
import com.pgoogol.finance.transaction.domain.TransactionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/finance/api/v1/transactions")
@Tag(name = "transactions", description = "Wydatki, przychody i transfery")
@RequiredArgsConstructor
public class TransactionController {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final TransactionService transactionService;
    private final CurrencyService currencyService;
    private final TransactionApiMapper mapper;

    @GetMapping
    @Operation(summary = "Transakcje z filtrami i stronicowaniem",
        description = """
            Filtr konta obejmuje też stronę docelową transferu — przelew pojawia \
            się w historii obu kont.""")
    public PageResponse<TransactionResponse> searchTransactions(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String currency,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {

        TransactionSearchCriteria criteria =
            new TransactionSearchCriteria(from, to, accountId, categoryId, type, currency);
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), cappedSize(size));
        Page<Transaction> found = transactionService.search(criteria, pageRequest);
        return PageResponse.of(found, this::toResponse);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Pojedyncza transakcja")
    public TransactionResponse getTransaction(@PathVariable long id) {

        Transaction transaction = transactionService.get(id);
        return toResponse(transaction);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Nowa transakcja",
        description = """
            Kwota jest zawsze dodatnia — kierunek wynika z pola type. Kurs \
            i kwota w walucie bazowej wyliczają się przy zapisie i już się \
            nie zmieniają.""")
    public TransactionResponse createTransaction(@Valid @RequestBody TransactionRequest request) {

        TransactionCommand command = mapper.toCommand(request);
        Transaction created = transactionService.create(command);
        return toResponse(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Zmiana transakcji",
        description = """
            Kurs jest wyznaczany od nowa dla daty księgowania po zmianie — inaczej \
            przesunięcie daty zostawiłoby kwotę bazową policzoną dla starej.""")
    public TransactionResponse updateTransaction(@PathVariable long id,
                                                 @Valid @RequestBody TransactionRequest request) {
        TransactionCommand command = mapper.toCommand(request);
        Transaction updated = transactionService.update(id, command);
        return toResponse(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Usunięcie transakcji")
    public void deleteTransaction(@PathVariable long id) {

        transactionService.delete(id);
    }

    private TransactionResponse toResponse(Transaction transaction) {

        String baseCurrency = currencyService.baseCurrency();
        return mapper.toResponse(transaction, baseCurrency);
    }

    /** Górny limit strony pilnujemy tutaj, nie w konfiguracji klienta. */
    static int cappedSize(int size) {

        return Math.clamp(size, 1, MAX_PAGE_SIZE);
    }
}
