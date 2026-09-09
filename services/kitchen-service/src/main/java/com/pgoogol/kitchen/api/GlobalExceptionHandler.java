package com.pgoogol.kitchen.api;

import com.pgoogol.kitchen.common.ConflictException;
import com.pgoogol.kitchen.common.ErrorCodes;
import com.pgoogol.kitchen.common.ExceptionMessageConstants;
import com.pgoogol.kitchen.common.NotFoundException;
import com.pgoogol.kitchen.common.ValidationException;
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

import java.util.List;
import java.util.stream.Collectors;

/**
 * Jedno miejsce, w którym wyjątek zamienia się w odpowiedź. Kod błędu jest
 * kontraktem dla frontu, treść — informacją dla człowieka; stack trace, SQL
 * i nazwy klas nie wychodzą stąd nigdy.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NotFoundException exception) {

        log.warn("Nie znaleziono zasobu: {}", exception.getErrorCode());
        return ErrorResponse.of(exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(ValidationException exception) {

        log.warn("Żądanie odrzucone: {}", exception.getErrorCode());
        return ErrorResponse.of(exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(ConflictException exception) {

        log.warn("Konflikt stanu: {}", exception.getErrorCode());
        return ErrorResponse.of(exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBeanValidation(MethodArgumentNotValidException exception) {

        List<String> problems = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
            .toList();
        String message = problems.stream().collect(Collectors.joining("; "));
        log.warn("Walidacja nie przeszła: {}", message);
        return ErrorResponse.of(ErrorCodes.VALIDATION_FAILED, message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException exception) {

        String message = ExceptionMessageConstants.INVALID_PARAMETER.formatted(exception.getName());
        return ErrorResponse.of(ErrorCodes.INVALID_PARAMETER, message);
    }

    /**
     * Równoległa edycja tego samego przepisu. Zapis, który przegrał wyścig, ma
     * dostać jasną informację, a nie po cichu wygrać kosztem cudzej zmiany.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOptimisticLock(OptimisticLockingFailureException exception) {

        log.warn("Równoległa zmiana zasobu", exception);
        return ErrorResponse.of(ErrorCodes.RECIPE_MODIFIED, ExceptionMessageConstants.RECIPE_MODIFIED);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrity(DataIntegrityViolationException exception) {

        log.warn("Naruszenie spójności danych", exception);
        return ErrorResponse.of(ErrorCodes.DATA_INTEGRITY_VIOLATION,
            ExceptionMessageConstants.DATA_INTEGRITY_VIOLATION);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception exception) {

        log.error("Nieobsłużony błąd", exception);
        return ErrorResponse.of(ErrorCodes.INTERNAL_ERROR, ExceptionMessageConstants.INTERNAL_ERROR);
    }
}
