package com.pgoogol.llm.exception;

/**
 * Provider odpowiedział, ale z odpowiedzi nic nie wynika: jest pusta, urwana
 * limitem tokenów albo nie odwzorowuje się na oczekiwany typ. Kod rozróżnia te
 * przypadki, bo reakcja na każdy jest inna.
 */
public class LlmResponseException extends LlmException {

    public LlmResponseException(String errorCode, String message) {

        super(errorCode, message);
    }

    public LlmResponseException(String errorCode, String message, Throwable cause) {

        super(errorCode, message, cause);
    }
}
