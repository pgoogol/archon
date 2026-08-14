package com.pgoogol.httpexchangelogger.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "type",
        "requestId",
        "traceId",
        "spanId",
        "method",
        "path",
        "queryString",
        "clientIp",
        "status",
        "durationMs",
        "configuredMode",
        "effectiveMode",
        "requestHeaders",
        "responseHeaders",
        "requestBody",
        "responseBody",
        "requestBodyTruncated",
        "responseBodyTruncated",
        "exceptionClass",
        "exceptionMessage"
})
public final class HttpExchangeLogEvent {

    private final String type;
    private final String requestId;
    private final String traceId;
    private final String spanId;
    private final String method;
    private final String path;
    private final String queryString;
    private final String clientIp;
    private final int status;
    private final long durationMs;
    private final HttpLogMode configuredMode;
    private final HttpLogMode effectiveMode;
    private final Map<String, List<String>> requestHeaders;
    private final Map<String, List<String>> responseHeaders;
    private final Object requestBody;
    private final Object responseBody;
    private final boolean requestBodyTruncated;
    private final boolean responseBodyTruncated;
    private final String exceptionClass;
    private final String exceptionMessage;

    private HttpExchangeLogEvent(Builder builder) {

        this.type = builder.type;
        this.requestId = builder.requestId;
        this.traceId = builder.traceId;
        this.spanId = builder.spanId;
        this.method = builder.method;
        this.path = builder.path;
        this.queryString = builder.queryString;
        this.clientIp = builder.clientIp;
        this.status = builder.status;
        this.durationMs = builder.durationMs;
        this.configuredMode = builder.configuredMode;
        this.effectiveMode = builder.effectiveMode;
        this.requestHeaders = builder.requestHeaders;
        this.responseHeaders = builder.responseHeaders;
        this.requestBody = builder.requestBody;
        this.responseBody = builder.responseBody;
        this.requestBodyTruncated = builder.requestBodyTruncated;
        this.responseBodyTruncated = builder.responseBodyTruncated;
        this.exceptionClass = builder.exceptionClass;
        this.exceptionMessage = builder.exceptionMessage;
    }

    public static Builder builder() {

        return new Builder();
    }

    public String getType() {

        return type;
    }

    public String getRequestId() {

        return requestId;
    }

    public String getTraceId() {

        return traceId;
    }

    public String getSpanId() {

        return spanId;
    }

    public String getMethod() {

        return method;
    }

    public String getPath() {

        return path;
    }

    public String getQueryString() {

        return queryString;
    }

    public String getClientIp() {

        return clientIp;
    }

    public int getStatus() {

        return status;
    }

    public long getDurationMs() {

        return durationMs;
    }

    public HttpLogMode getConfiguredMode() {

        return configuredMode;
    }

    public HttpLogMode getEffectiveMode() {

        return effectiveMode;
    }

    public Map<String, List<String>> getRequestHeaders() {

        return requestHeaders;
    }

    public Map<String, List<String>> getResponseHeaders() {

        return responseHeaders;
    }

    public Object getRequestBody() {

        return requestBody;
    }

    public Object getResponseBody() {

        return responseBody;
    }

    public boolean isRequestBodyTruncated() {

        return requestBodyTruncated;
    }

    public boolean isResponseBodyTruncated() {

        return responseBodyTruncated;
    }

    public String getExceptionClass() {

        return exceptionClass;
    }

    public String getExceptionMessage() {

        return exceptionMessage;
    }

    public static final class Builder {

        private String type = "http_exchange";
        private String requestId;
        private String traceId;
        private String spanId;
        private String method;
        private String path;
        private String queryString;
        private String clientIp;
        private int status;
        private long durationMs;
        private HttpLogMode configuredMode;
        private HttpLogMode effectiveMode;
        private Map<String, List<String>> requestHeaders;
        private Map<String, List<String>> responseHeaders;
        private Object requestBody;
        private Object responseBody;
        private boolean requestBodyTruncated;
        private boolean responseBodyTruncated;
        private String exceptionClass;
        private String exceptionMessage;

        private Builder() {

        }

        public Builder type(String type) {

            this.type = type;
            return this;
        }

        public Builder requestId(String requestId) {

            this.requestId = requestId;
            return this;
        }

        public Builder traceId(String traceId) {

            this.traceId = traceId;
            return this;
        }

        public Builder spanId(String spanId) {

            this.spanId = spanId;
            return this;
        }

        public Builder method(String method) {

            this.method = method;
            return this;
        }

        public Builder path(String path) {

            this.path = path;
            return this;
        }

        public Builder queryString(String queryString) {

            this.queryString = queryString;
            return this;
        }

        public Builder clientIp(String clientIp) {

            this.clientIp = clientIp;
            return this;
        }

        public Builder status(int status) {

            this.status = status;
            return this;
        }

        public Builder durationMs(long durationMs) {

            this.durationMs = durationMs;
            return this;
        }

        public Builder configuredMode(HttpLogMode configuredMode) {

            this.configuredMode = configuredMode;
            return this;
        }

        public Builder effectiveMode(HttpLogMode effectiveMode) {

            this.effectiveMode = effectiveMode;
            return this;
        }

        public Builder requestHeaders(Map<String, List<String>> requestHeaders) {

            this.requestHeaders = requestHeaders;
            return this;
        }

        public Builder responseHeaders(Map<String, List<String>> responseHeaders) {

            this.responseHeaders = responseHeaders;
            return this;
        }

        public Builder requestBody(Object requestBody) {

            this.requestBody = requestBody;
            return this;
        }

        public Builder responseBody(Object responseBody) {

            this.responseBody = responseBody;
            return this;
        }

        public Builder requestBodyTruncated(boolean requestBodyTruncated) {

            this.requestBodyTruncated = requestBodyTruncated;
            return this;
        }

        public Builder responseBodyTruncated(boolean responseBodyTruncated) {

            this.responseBodyTruncated = responseBodyTruncated;
            return this;
        }

        public Builder exceptionClass(String exceptionClass) {

            this.exceptionClass = exceptionClass;
            return this;
        }

        public Builder exceptionMessage(String exceptionMessage) {

            this.exceptionMessage = exceptionMessage;
            return this;
        }

        public HttpExchangeLogEvent build() {

            return new HttpExchangeLogEvent(this);
        }

    }

}
