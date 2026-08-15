package com.pgoogol.httpexchangelogger.sanitizer;

import com.pgoogol.httpexchangelogger.autoconfigure.HttpExchangeLoggerProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DefaultHeaderSanitizer implements HeaderSanitizer {

    private static final List<String> DEFAULT_SENSITIVE_HEADERS = List.of("authorization", "cookie", "set-cookie", "x-api-key");

    private final SensitiveValueMasker masker;
    private final SensitiveValueMasker defaultHeaderMasker;
    private final boolean maskingEnabled;

    public DefaultHeaderSanitizer(SensitiveValueMasker masker, HttpExchangeLoggerProperties.Mask maskProperties) {

        this.masker = masker;
        this.defaultHeaderMasker = new SensitiveValueMasker(DEFAULT_SENSITIVE_HEADERS);
        this.maskingEnabled = Objects.nonNull(maskProperties) && maskProperties.isEnabled();
    }

    @Override
    public Map<String, List<String>> sanitize(Map<String, List<String>> headers) {

        if (Objects.isNull(headers) || headers.isEmpty()) {

            return headers;
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        headers.forEach((name, values) -> result.put(name, sanitizeValues(name, values)));
        return result;
    }

    private List<String> sanitizeValues(String name, List<String> values) {

        if (isSensitiveHeader(name)) {

            return maskAll(values);
        }
        if (Objects.isNull(values)) {

            return Collections.emptyList();
        }
        return new ArrayList<>(values);
    }

    private boolean isSensitiveHeader(String name) {

        if (Objects.isNull(name)) {

            return false;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        // Built-in credential headers are always masked, even when mask.enabled=false,
        // so disabling field masking can never leak Authorization/Cookie/etc.
        if (defaultHeaderMasker.isSensitive(normalized)) {

            return true;
        }
        return maskingEnabled && masker.isSensitive(normalized);
    }

    private List<String> maskAll(List<String> values) {

        if (Objects.isNull(values) || values.isEmpty()) {

            return List.of(SensitiveValueMasker.MASK);
        }
        return IntStream.range(0, values.size()) //
                .mapToObj(i -> SensitiveValueMasker.MASK) //
                .collect(Collectors.toCollection(() -> new ArrayList<>(values.size())));
    }

}
