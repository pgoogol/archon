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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = HttpExchangeLoggingFilterDisabledTest.TestApp.class)
@TestPropertySource(properties = {
        "http-exchange-logger.enabled=false",
        "http-exchange-logger.default-mode=FULL"
})
class HttpExchangeLoggingFilterDisabledTest {

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
    void doFilter_whenEnabledIsFalse_emitsNoEventsAndDoesNotSetRequestIdHeader() throws Exception {

        // given / when
        MvcResult result = mockMvc().perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p_1\"}"))
                .andExpect(status().isOk())
                .andReturn();

        // then
        assertThat(logs.getEvents()).isEmpty();
        assertThat(result.getResponse().getHeader("X-Request-Id")).isNull();
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
