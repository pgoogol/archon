package com.pgoogol.httpexchangelogger.sanitizer;

import com.pgoogol.httpexchangelogger.autoconfigure.HttpExchangeLoggerProperties;
import com.pgoogol.httpexchangelogger.support.ContentTypeMatcher;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class FormBodySanitizer implements BodySanitizer {

    private final SensitiveValueMasker masker;
    private final boolean maskingEnabled;

    public FormBodySanitizer(SensitiveValueMasker masker, HttpExchangeLoggerProperties.Mask maskProperties) {

        this.masker = masker;
        this.maskingEnabled = Objects.nonNull(maskProperties) && maskProperties.isEnabled();
    }

    @Override
    public String sanitize(String body, String contentType) {

        if (!StringUtils.hasLength(body)) {

            return body;
        }
        if (!maskingEnabled) {

            return body;
        }
        if (!ContentTypeMatcher.isFormUrlEncoded(contentType)) {

            return body;
        }
        return Arrays.stream(body.split("&"))
                .map(this::maskPair)
                .collect(Collectors.joining("&"));
    }

    private String maskPair(String pair) {

        int equals = pair.indexOf('=');
        if (equals < 0) {

            return pair;
        }
        String encodedKey = pair.substring(0, equals);
        String decodedKey = decode(encodedKey);
        if (masker.isSensitive(decodedKey)) {

            return encodedKey + "=" + SensitiveValueMasker.MASK;
        }
        return pair;
    }

    private String decode(String value) {

        try {

            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception ex) {

            return value;
        }
    }

}
