package com.pgoogol.httpexchangelogger.integration;

import com.pgoogol.httpexchangelogger.filter.HttpExchangeLoggingFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = HttpExchangeLoggingFilterIntegrationTest.TestApp.class)
@TestPropertySource(properties = {
        "http-exchange-logger.enabled=true",
        "http-exchange-logger.default-mode=LIMITED",
        "http-exchange-logger.max-body-length=200",
        "http-exchange-logger.limited-max-body-length=50",
        "http-exchange-logger.endpoints[0].pattern=/api/orders",
        "http-exchange-logger.endpoints[0].mode=FULL",
        "http-exchange-logger.endpoints[1].pattern=/api/orders/**",
        "http-exchange-logger.endpoints[1].mode=FULL",
        "http-exchange-logger.endpoints[2].pattern=/api/auth/**",
        "http-exchange-logger.endpoints[2].mode=BASIC",
        "http-exchange-logger.endpoints[3].pattern=/actuator/**",
        "http-exchange-logger.endpoints[3].mode=OFF",
        "http-exchange-logger.endpoints[4].pattern=/api/boom",
        "http-exchange-logger.endpoints[4].mode=FULL",
        "http-exchange-logger.endpoints[5].pattern=/api/binary",
        "http-exchange-logger.endpoints[5].mode=FULL",
        "http-exchange-logger.endpoints[6].pattern=/api/products",
        "http-exchange-logger.endpoints[6].mode=LIMITED",
        "http-exchange-logger.endpoints[7].pattern=/api/form",
        "http-exchange-logger.endpoints[7].mode=FULL",
        "http-exchange-logger.endpoints[8].pattern=/api/xml",
        "http-exchange-logger.endpoints[8].mode=FULL"
})
class HttpExchangeLoggingFilterIntegrationTest {

    private final HttpExchangeLogCapture logs = new HttpExchangeLogCapture();

    @Autowired
    WebApplicationContext webApplicationContext;

    @Autowired
    HttpExchangeLoggingFilter filter;

    private MockMvc mockMvc() {

        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(filter)
                .build();
    }

    @BeforeEach
    void attachCapture() {

        logs.attach();
    }

    @AfterEach
    void detachCapture() {

        logs.detach();
    }

    @Test
    void doFilter_whenModeBasic_logsOnlyMetadata() throws Exception {

        // given / when
        MvcResult result = mockMvc().perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andReturn();

        // then
        assertThat(result.getResponse().getContentAsString()).contains("fake-token");

        JsonNode event = logs.last();
        assertThat(event.path("method").asString()).isEqualTo("POST");
        assertThat(event.path("path").asString()).isEqualTo("/api/auth/login");
        assertThat(event.path("status").asInt()).isEqualTo(200);
        assertThat(event.path("configuredMode").asString()).isEqualTo("BASIC");
        assertThat(event.path("requestBody").isMissingNode()).isTrue();
        assertThat(event.path("responseBody").isMissingNode()).isTrue();
        assertThat(event.path("requestHeaders").isMissingNode()).isTrue();
        assertThat(event.path("responseHeaders").isMissingNode()).isTrue();
        assertThat(event.path("requestId").asString()).isNotBlank();
    }

    @Test
    void doFilter_whenModeOff_skipsLogging() throws Exception {

        // given / when
        mockMvc().perform(get("/actuator/health"))
                .andExpect(status().isOk());

        // then
        assertThat(logs.getEvents()).isEmpty();
    }

    @Test
    void doFilter_whenModeOff_stillSetsRequestIdHeader() throws Exception {

        // given / when
        MvcResult result = mockMvc().perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn();

        // then
        assertThat(logs.getEvents()).isEmpty();
        assertThat(result.getResponse().getHeader("X-Request-Id")).isNotBlank();
    }

    @Test
    void doFilter_whenModeFull_includesBodiesAndMasksSensitiveFields() throws Exception {

        // given / when
        MvcResult result = mockMvc().perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer my-token")
                        .content("{\"productId\":\"p_123\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andReturn();

        // then
        assertThat(result.getResponse().getContentAsString()).contains("ord_456");

        JsonNode event = logs.last();
        assertThat(event.path("configuredMode").asString()).isEqualTo("FULL");
        // Body is kept as a JSON node so it serializes as a nested object, not an escaped string.
        assertThat(event.path("requestBody").isObject()).isTrue();
        assertThat(event.path("requestBody").toString()).contains("\"password\":\"***\"");
        assertThat(event.path("requestBody").toString()).contains("\"productId\":\"p_123\"");
        assertThat(event.path("requestHeaders").isObject()).isTrue();
        assertThat(extractHeader(event.path("requestHeaders"), "Authorization").toString()).contains("***");
        assertThat(event.path("responseBody").toString()).contains("ord_456");
    }

    @Test
    void doFilter_whenSecretSitsPastMaxBodyLength_doesNotLeakSecret() throws Exception {

        // given
        // A large JSON body whose secret sits past max-body-length must still be masked,
        // never echoed raw because truncation produced invalid JSON.
        String body = "{\"password\":\"hunter2\",\"filler\":\"%s\"}".formatted("x".repeat(500));

        // when
        mockMvc().perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // then
        JsonNode event = logs.last();
        assertThat(event.path("requestBody").toString()).doesNotContain("hunter2");
    }

    @Test
    void doFilter_whenBodyExceedsMaxLength_marksRequestBodyTruncated() throws Exception {

        // given
        String big = "{\"data\":\"%s\"}".formatted("x".repeat(500));

        // when
        mockMvc().perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(big))
                .andExpect(status().isOk());

        // then
        JsonNode event = logs.last();
        assertThat(event.path("requestBodyTruncated").asBoolean()).isTrue();
    }

    @Test
    void doFilter_whenRequestHasQueryString_capturesPathAndQueryString() throws Exception {

        // given / when
        mockMvc().perform(get("/api/orders/123?source=mobile"))
                .andExpect(status().isOk());

        // then
        JsonNode event = logs.last();
        assertThat(event.path("path").asString()).isEqualTo("/api/orders/123");
        assertThat(event.path("queryString").asString()).isEqualTo("source=mobile");
    }

    @Test
    void doFilter_whenHandlerThrows_capturesExceptionAndStillLogs() throws Exception {

        // given / when
        try {

            mockMvc().perform(get("/api/boom"));
        } catch (Exception ignored) {

            // exception is propagated by MockMvc / dispatcher
        }

        // then
        JsonNode event = logs.last();
        assertThat(event.path("exceptionClass").asString()).isEqualTo(IllegalStateException.class.getName());
        assertThat(event.path("exceptionMessage").asString()).isEqualTo("Cannot create order");
        // Status must reflect a server error, not the default 200 still on the response.
        assertThat(event.path("status").asInt()).isGreaterThanOrEqualTo(500);
    }

    @Test
    void doFilter_whenContentTypeIsBinary_doesNotLogBodies() throws Exception {

        // given / when
        mockMvc().perform(post("/api/binary")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[]{1, 2, 3}))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1, 2, 3}));

        // then
        JsonNode event = logs.last();
        assertThat(event.path("requestBody").asString()).contains("not logged");
        assertThat(event.path("responseBody").asString()).contains("not logged");
    }

    @Test
    void doFilter_whenHandlerIgnoresRequestBody_stillLogsIt() throws Exception {

        // given / when
        mockMvc().perform(post("/api/ignore-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p_1\"}"))
                .andExpect(status().isOk());

        // then
        JsonNode event = logs.last();
        assertThat(event.path("requestBody").isMissingNode()).isFalse();
        assertThat(event.path("requestBody").toString()).contains("p_1");
    }

    @Test
    void doFilter_whenResponseHasBody_stillReachesClient() throws Exception {

        // given / when
        MvcResult result = mockMvc().perform(get("/api/orders/123?source=mobile"))
                .andExpect(status().isOk())
                .andReturn();

        // then
        assertThat(result.getResponse().getContentAsString()).contains("\"orderId\":\"123\"");
        assertThat(result.getResponse().getHeader("X-Request-Id")).isNotBlank();
    }

    @Test
    void doFilter_whenIncomingRequestIdHeaderPresent_honorsIt() throws Exception {

        // given / when
        MvcResult result = mockMvc().perform(get("/api/orders/123").header("X-Request-Id", "external-id"))
                .andExpect(status().isOk())
                .andReturn();

        // then
        assertThat(result.getResponse().getHeader("X-Request-Id")).isEqualTo("external-id");
        JsonNode event = logs.last();
        assertThat(event.path("requestId").asString()).isEqualTo("external-id");
    }

    @Test
    void doFilter_whenModeLimited_appliesLimitedMaxBodyLengthAndCapsEarlierThanFull() throws Exception {

        // given
        // 100-char body: 100 > limited-max-body-length (50) so LIMITED truncates,
        // but 100 < max-body-length (200) so FULL keeps it intact.
        String body = "{\"productId\":\"p_1\",\"data\":\"%s\"}".formatted("x".repeat(70));

        // when
        mockMvc().perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        JsonNode limitedEvent = logs.last();

        logs.clear();
        mockMvc().perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        JsonNode fullEvent = logs.last();

        // then
        assertThat(limitedEvent.path("configuredMode").asString()).isEqualTo("LIMITED");
        assertThat(limitedEvent.path("requestBodyTruncated").asBoolean()).isTrue();
        assertThat(fullEvent.path("configuredMode").asString()).isEqualTo("FULL");
        assertThat(fullEvent.path("requestBodyTruncated").asBoolean()).isFalse();
    }

    @Test
    void doFilter_whenFormUrlEncodedRequest_logsBodyAndMasksSensitiveFields() throws Exception {

        // given / when
        mockMvc().perform(post("/api/form")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("user=alice&password=hunter2"))
                .andExpect(status().isOk());

        // then
        JsonNode event = logs.last();
        assertThat(event.path("requestBody").isMissingNode()).isFalse();
        String logged = event.path("requestBody").asString();
        assertThat(logged).contains("user=alice");
        assertThat(logged).contains("password=***");
        assertThat(logged).doesNotContain("hunter2");
    }

    @Test
    void doFilter_whenXmlRequest_logsBodyAndMasksSensitiveElements() throws Exception {

        // given
        String body = "<order><password>secret</password><productId>p_1</productId></order>";

        // when
        mockMvc().perform(post("/api/xml")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(body))
                .andExpect(status().isOk());

        // then
        JsonNode event = logs.last();
        assertThat(event.path("requestBody").isMissingNode()).isFalse();
        String logged = event.path("requestBody").asString();
        assertThat(logged).contains("<password>***</password>");
        assertThat(logged).contains("<productId>p_1</productId>");
        assertThat(logged).doesNotContain("secret");
    }

    private JsonNode extractHeader(JsonNode headers, String key) {

        for (Map.Entry<String, JsonNode> entry : headers.properties()) {

            if (entry.getKey().equalsIgnoreCase(key)) {

                return entry.getValue();
            }
        }
        return headers.path(key);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {

        @Bean
        TestController testController() {

            return new TestController();
        }

    }

}
