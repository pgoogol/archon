package com.pgoogol.llm.exception;

import lombok.experimental.UtilityClass;

/**
 * Kody błędów startera. Serwis mapuje je na własne — starter nie zna ani kodów
 * HTTP serwisu, ani jego formatu odpowiedzi.
 */
@UtilityClass
public class LlmErrorCodes {

    public static final String NOT_CONFIGURED = "LLM_NOT_CONFIGURED";
    public static final String RATE_LIMITED = "LLM_RATE_LIMITED";
    public static final String UNAVAILABLE = "LLM_UNAVAILABLE";
    public static final String REQUEST_REJECTED = "LLM_REQUEST_REJECTED";
    public static final String UNSUPPORTED_INPUT = "LLM_UNSUPPORTED_INPUT";
    public static final String RESPONSE_EMPTY = "LLM_RESPONSE_EMPTY";
    public static final String RESPONSE_TRUNCATED = "LLM_RESPONSE_TRUNCATED";
    public static final String RESPONSE_INVALID = "LLM_RESPONSE_INVALID";
}
