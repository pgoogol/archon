package com.pgoogol.httpexchangelogger.integration;

import com.pgoogol.httpexchangelogger.filter.HttpExchangeLoggingFilter;
import com.pgoogol.httpexchangelogger.model.HttpLogMode;
import com.pgoogol.httpexchangelogger.runtime.RuntimeModeOverrideManager;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = HttpExchangeLoggingFilterOverrideTest.TestApp.class)
@TestPropertySource(properties = {
        "http-exchange-logger.default-mode=BASIC"
})
class HttpExchangeLoggingFilterOverrideTest {

    private final HttpExchangeLogCapture logs = new HttpExchangeLogCapture();

    @Autowired
    WebApplicationContext webApplicationContext;

    @Autowired
    HttpExchangeLoggingFilter filter;

    @Autowired
    RuntimeModeOverrideManager overrideManager;

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

        overrideManager.clearAll();
        logs.detach();
    }

    @Test
    void doFilter_whenGlobalFullOverrideActive_logsBodyAndReportsBothModes() throws Exception {

        // given
        overrideManager.setGlobalOverride(HttpLogMode.FULL, null);

        // when
        mockMvc().perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p_1\"}"))
                .andExpect(status().isOk());

        // then
        JsonNode event = logs.last();
        assertThat(event.path("configuredMode").asString()).isEqualTo("BASIC");
        assertThat(event.path("effectiveMode").asString()).isEqualTo("FULL");
        assertThat(event.path("requestBody").isMissingNode()).isFalse();
    }

    @Test
    void doFilter_whenPatternOffOverrideActive_suppressesLoggingOnlyForMatchingPaths() throws Exception {

        // given
        overrideManager.setPatternOverride("/api/orders/**", HttpLogMode.OFF, null);

        // when a matching endpoint is called
        mockMvc().perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p_1\"}"))
                .andExpect(status().isOk());

        // then nothing is logged for it
        assertThat(logs.getEvents()).isEmpty();

        // when a non-matching endpoint is called
        mockMvc().perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"keyboard\"}"))
                .andExpect(status().isOk());

        // then the configured mode still applies there
        assertThat(logs.getEvents()).hasSize(1);
    }

    @Test
    void doFilter_whenNoOverride_usesConfiguredMode() throws Exception {

        // given no override

        // when
        mockMvc().perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p_1\"}"))
                .andExpect(status().isOk());

        // then
        JsonNode event = logs.last();
        assertThat(event.path("configuredMode").asString()).isEqualTo("BASIC");
        assertThat(event.path("effectiveMode").asString()).isEqualTo("BASIC");
        assertThat(event.path("requestBody").isMissingNode()).isTrue();
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
