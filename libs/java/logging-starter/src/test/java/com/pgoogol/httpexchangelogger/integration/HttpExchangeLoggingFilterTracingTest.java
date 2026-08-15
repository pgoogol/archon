package com.pgoogol.httpexchangelogger.integration;

import com.pgoogol.httpexchangelogger.filter.HttpExchangeLoggingFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = HttpExchangeLoggingFilterTracingTest.TestApp.class)
class HttpExchangeLoggingFilterTracingTest {

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
    void cleanUp() {

        MDC.clear();
        logs.detach();
    }

    @Test
    void doFilter_whenMdcCarriesTraceContext_eventContainsTraceAndSpanIds() throws Exception {

        // given the tracing setup propagated ids into the MDC (as micrometer-tracing does)
        MDC.put("traceId", "trace-123");
        MDC.put("spanId", "span-456");

        // when
        mockMvc().perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p_1\"}"))
                .andExpect(status().isOk());

        // then
        JsonNode event = logs.last();
        assertThat(event.path("traceId").asString()).isEqualTo("trace-123");
        assertThat(event.path("spanId").asString()).isEqualTo("span-456");
    }

    @Test
    void doFilter_whenNoTraceContext_eventHasNoTraceIds() throws Exception {

        // given an empty MDC

        // when
        mockMvc().perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p_1\"}"))
                .andExpect(status().isOk());

        // then
        JsonNode event = logs.last();
        assertThat(event.path("traceId").isMissingNode()).isTrue();
        assertThat(event.path("spanId").isMissingNode()).isTrue();
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
