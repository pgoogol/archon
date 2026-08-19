package com.pgoogol.finance.api;

import com.pgoogol.finance.common.ConflictException;
import com.pgoogol.finance.common.ExternalServiceException;
import com.pgoogol.finance.common.NotFoundException;
import com.pgoogol.finance.common.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Jedyny punkt mapowania wyjątków na odpowiedzi błędów — bez rozproszonych
 * {@code @ExceptionHandler} po kontrolerach.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(ValidationException ex) {

        log.warn("Odrzucone żądanie: {} — {}", ex.getErrorCode(), ex.getMessage());
        return ErrorResponse.of(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBeanValidation(MethodArgumentNotValidException ex) {

        String details = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .sorted()
            .collect(Collectors.joining("; "));
        log.warn("Walidacja ciała żądania nie przeszła: {}", details);
        return ErrorResponse.of("VALIDATION_FAILED", details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleParameterTypeMismatch(MethodArgumentTypeMismatchException ex) {

        // np. type=WYDATEK albo from=wczoraj — wartość spoza typu to błąd klienta
        log.warn("Niepoprawny parametr '{}': {}", ex.getName(), ex.getValue());
        return ErrorResponse.of("INVALID_PARAMETER",
            "Niepoprawna wartość parametru '%s': %s".formatted(ex.getName(), ex.getValue()));
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(ConflictException ex) {

        log.warn("Konflikt: {} — {}", ex.getErrorCode(), ex.getMessage());
        return ErrorResponse.of(ex.getErrorCode(), ex.getMessage());
    }

    /**
     * Więzy bazy są ostatnią linią obrony kształtu danych. Jeśli tu dotarliśmy,
     * walidacja domenowa czegoś nie objęła — dlatego log ma poziom ERROR,
     * a użytkownik nie dostaje treści SQL.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrity(DataIntegrityViolationException ex) {

        log.error("Naruszenie więzów bazy przepuszczone przez walidację domenową", ex);
        return ErrorResponse.of("DATA_INTEGRITY_VIOLATION",
            "Zapis narusza spójność danych");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOptimisticLock(OptimisticLockingFailureException ex) {

        log.info("Konflikt zapisu (blokada optymistyczna): {}", ex.getMessage());
        return ErrorResponse.of("RESOURCE_MODIFIED",
            "Wpis zmienił się w innym miejscu — odśwież i spróbuj ponownie");
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NotFoundException ex) {

        log.warn("Zasób nie istnieje: {}", ex.getErrorCode());
        return ErrorResponse.of(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(ExternalServiceException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse handleExternalService(ExternalServiceException ex) {

        log.error("Zewnętrzne API zawiodło: {} — {}", ex.getErrorCode(), ex.getMessage());
        return ErrorResponse.of(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception ex) {

        log.error("Nieoczekiwany błąd", ex);
        return ErrorResponse.of("INTERNAL_ERROR", "Wystąpił nieoczekiwany błąd");
    }
}
