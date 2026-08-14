package com.pgoogol.httpexchangelogger.support;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public class DefaultClientIpExtractor implements ClientIpExtractor {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";

    @Override
    public String extract(HttpServletRequest request) {

        if (Objects.isNull(request)) {

            return null;
        }

        String forwarded = request.getHeader(X_FORWARDED_FOR);
        if (Objects.nonNull(forwarded) && !forwarded.isBlank()) {

            Optional<String> forwardedClient = Arrays.stream(forwarded.split(","))
                    .map(String::trim)
                    .filter(candidate -> !candidate.isEmpty() && !candidate.equalsIgnoreCase("unknown"))
                    .findFirst();
            if (forwardedClient.isPresent()) {

                return forwardedClient.get();
            }
        }

        String realIp = request.getHeader(X_REAL_IP);
        if (Objects.nonNull(realIp) && !realIp.isBlank()) {

            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

}
