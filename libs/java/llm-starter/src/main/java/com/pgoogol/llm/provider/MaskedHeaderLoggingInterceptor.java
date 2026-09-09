package com.pgoogol.llm.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loguje na DEBUG, co poszło do providera — z nagłówkami niosącymi klucz
 * zastąpionymi maską. Treści żądania nie logujemy: prompt bywa wielkości
 * strony, a przy imporcie ze zdjęcia niesie cały obraz w base64.
 */
public class MaskedHeaderLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(MaskedHeaderLoggingInterceptor.class);
    private static final Set<String> SECRET_HEADERS = Set.of("x-api-key", "authorization");

    private final String clientName;

    public MaskedHeaderLoggingInterceptor(String clientName) {

        this.clientName = clientName;
    }

    @Override
    @NonNull
    public ClientHttpResponse intercept(@NonNull HttpRequest request,
                                        @NonNull byte[] body,
                                        @NonNull ClientHttpRequestExecution execution) throws IOException {

        if (log.isDebugEnabled()) {

            log.debug("Klient LLM {} woła {} {} z nagłówkami {}", clientName, request.getMethod(),
                    request.getURI(), maskedHeaders(request.getHeaders()));
        }
        return execution.execute(request, body);
    }

    private String maskedHeaders(HttpHeaders headers) {

        return headers.headerSet().stream()
                .map(entry -> "%s=%s".formatted(entry.getKey(), maskedValues(entry.getKey(), entry.getValue())))
                .collect(Collectors.joining(", "));
    }

    private String maskedValues(String name, List<String> values) {

        if (SECRET_HEADERS.contains(name.toLowerCase())) {

            return SecretMasker.maskedHeaderValue();
        }
        return String.join(",", values);
    }
}
