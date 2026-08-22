package com.pgoogol.finance.common;

import lombok.experimental.UtilityClass;

/**
 * Kody błędów w jednym miejscu. To one są kontraktem dla klienta — front
 * rozpoznaje przypadek po kodzie, nigdy po treści komunikatu — więc literał
 * wpisany w miejscu rzucenia wyjątku jest kontraktem pisanym z pamięci.
 *
 * <p>Jedna lista daje też odpowiedź na pytanie „jakie kody może zwrócić ten
 * serwis", której wcześniej udzielał wyłącznie grep. Treści komunikatów siedzą
 * obok, w {@link ExceptionMessageConstants}.</p>
 */
@UtilityClass
public class ErrorCodes {

    public static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";

    public static final String CATEGORY_NOT_FOUND = "CATEGORY_NOT_FOUND";
    public static final String CATEGORY_EXISTS = "CATEGORY_EXISTS";
    public static final String CATEGORY_CYCLE = "CATEGORY_CYCLE";
    public static final String CATEGORY_DIRECTION_MISMATCH = "CATEGORY_DIRECTION_MISMATCH";
    public static final String CATEGORY_REQUIRED = "CATEGORY_REQUIRED";

    public static final String CURRENCY_NOT_FOUND = "CURRENCY_NOT_FOUND";
    public static final String CURRENCY_EXISTS = "CURRENCY_EXISTS";
    public static final String CURRENCY_MISMATCH = "CURRENCY_MISMATCH";
    public static final String EXCHANGE_RATE_NOT_FOUND = "EXCHANGE_RATE_NOT_FOUND";
    public static final String BASE_CURRENCY_RATE = "BASE_CURRENCY_RATE";
    public static final String INVALID_DATE_RANGE = "INVALID_DATE_RANGE";

    public static final String TRANSACTION_NOT_FOUND = "TRANSACTION_NOT_FOUND";
    public static final String AMOUNT_NOT_POSITIVE = "AMOUNT_NOT_POSITIVE";
    public static final String ORIGINAL_AMOUNT_INCOMPLETE = "ORIGINAL_AMOUNT_INCOMPLETE";
    public static final String TRANSFER_WITHOUT_TARGET = "TRANSFER_WITHOUT_TARGET";
    public static final String TRANSFER_WITH_CATEGORY = "TRANSFER_WITH_CATEGORY";
    public static final String TRANSFER_TO_SAME_ACCOUNT = "TRANSFER_TO_SAME_ACCOUNT";
    public static final String TRANSFER_TARGET_AMOUNT_REQUIRED = "TRANSFER_TARGET_AMOUNT_REQUIRED";
    public static final String FLOW_WITH_TRANSFER_TARGET = "FLOW_WITH_TRANSFER_TARGET";

    public static final String IMPORT_BATCH_NOT_FOUND = "IMPORT_BATCH_NOT_FOUND";
    public static final String IMPORT_BATCH_ALREADY_COMMITTED = "IMPORT_BATCH_ALREADY_COMMITTED";
    public static final String STATEMENT_ALREADY_IMPORTED = "STATEMENT_ALREADY_IMPORTED";
    public static final String STATEMENT_FORMAT_UNKNOWN = "STATEMENT_FORMAT_UNKNOWN";
    public static final String STATEMENT_UNREADABLE = "STATEMENT_UNREADABLE";
    public static final String STATEMENT_EMPTY = "STATEMENT_EMPTY";
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";

    public static final String NBP_RATE_LIMITED = "NBP_RATE_LIMITED";
    public static final String NBP_UNAVAILABLE = "NBP_UNAVAILABLE";

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String INVALID_PARAMETER = "INVALID_PARAMETER";
    public static final String RESOURCE_MODIFIED = "RESOURCE_MODIFIED";
    public static final String DATA_INTEGRITY_VIOLATION = "DATA_INTEGRITY_VIOLATION";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
