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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = HttpExchangeLoggingFilterSamplingTest.TestApp.class)
@TestPropertySource(properties = {
        "http-exchange-logger.default-mode=FULL",
        "http-exchange-logger.sampling.rate=0.0"
})
class HttpExchangeLoggingFilterSamplingTest {

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
    void doFilter_whenSamplingRateIsZero_emitsNoEventsButStillHandlesRequest() throws Exception {

        // given / when
        MvcResult result = mockMvc().perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p_1\"}"))
                .andExpect(status().isOk())
                .andReturn();

        // then the request went through untouched, the request id is still issued, but nothing is logged
        assertThat(logs.getEvents()).isEmpty();
        assertThat(result.getResponse().getHeader("X-Request-Id")).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).contains("ord_456");
    }

    @Test
    void doFilter_whenRuntimeOverrideActive_bypassesSampling() throws Exception {

        // given sampling.rate=0 but an explicit runtime override
        overrideManager.setGlobalOverride(HttpLogMode.FULL, null);

        // when
        mockMvc().perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p_1\"}"))
                .andExpect(status().isOk());

        // then the exchange is logged despite the zero sampling rate
        assertThat(logs.getEvents()).hasSize(1);
        assertThat(logs.last().path("effectiveMode").asString()).isEqualTo("FULL");
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
