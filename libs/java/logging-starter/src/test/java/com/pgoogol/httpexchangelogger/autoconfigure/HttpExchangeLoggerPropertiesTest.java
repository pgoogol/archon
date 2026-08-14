package com.pgoogol.httpexchangelogger.autoconfigure;

import com.pgoogol.httpexchangelogger.model.HttpLogMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class HttpExchangeLoggerPropertiesTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HttpExchangeLoggerAutoConfiguration.class));

    @Test
    void properties_whenNoOverrides_bindReasonableDefaults() {

        // given / when / then
        runner.run(ctx -> {
            HttpExchangeLoggerProperties props = ctx.getBean(HttpExchangeLoggerProperties.class);
            assertThat(props.isEnabled()).isTrue();
            assertThat(props.getDefaultMode()).isEqualTo(HttpLogMode.BASIC);
            assertThat(props.getMaxBodyLength()).isEqualTo(10_000);
            assertThat(props.getLimitedMaxBodyLength()).isEqualTo(2_000);
            assertThat(props.isRequireTtlForFullLogging()).isFalse();
            assertThat(props.getInclude().isHeaders()).isTrue();
            assertThat(props.getInclude().isQueryString()).isTrue();
            assertThat(props.getInclude().isClientIp()).isTrue();
            assertThat(props.getMask().isEnabled()).isTrue();
            assertThat(props.getMask().getFields())
                    .containsExactlyInAnyOrder("password", "token", "accessToken", "refreshToken",
                            "authorization", "cookie", "pesel", "email");
        });
    }

    @Test
    void properties_whenOverridesProvided_bindConfiguredValues() {

        // given
        runner.withPropertyValues(
                "http-exchange-logger.enabled=true",
                "http-exchange-logger.default-mode=LIMITED",
                "http-exchange-logger.require-ttl-for-full-logging=true",
                "http-exchange-logger.max-body-length=512",
                "http-exchange-logger.limited-max-body-length=64",
                "http-exchange-logger.include.headers=false",
                "http-exchange-logger.include.query-string=false",
                "http-exchange-logger.include.client-ip=false",
                "http-exchange-logger.mask.enabled=false",
                "http-exchange-logger.mask.fields[0]=secretKey",
                "http-exchange-logger.endpoints[0].pattern=/api/orders/**",
                "http-exchange-logger.endpoints[0].mode=FULL",
                "http-exchange-logger.endpoints[1].pattern=/actuator/**",
                "http-exchange-logger.endpoints[1].mode=OFF"
        // when
        ).run(ctx -> {
            // then
            HttpExchangeLoggerProperties props = ctx.getBean(HttpExchangeLoggerProperties.class);
            assertThat(props.getDefaultMode()).isEqualTo(HttpLogMode.LIMITED);
            assertThat(props.isRequireTtlForFullLogging()).isTrue();
            assertThat(props.getMaxBodyLength()).isEqualTo(512);
            assertThat(props.getLimitedMaxBodyLength()).isEqualTo(64);
            assertThat(props.getInclude().isHeaders()).isFalse();
            assertThat(props.getInclude().isQueryString()).isFalse();
            assertThat(props.getInclude().isClientIp()).isFalse();
            assertThat(props.getMask().isEnabled()).isFalse();
            assertThat(props.getMask().getFields()).containsExactly("secretKey");
            assertThat(props.getEndpoints()).hasSize(2);
            assertThat(props.getEndpoints().get(0).getPattern()).isEqualTo("/api/orders/**");
            assertThat(props.getEndpoints().get(0).getMode()).isEqualTo(HttpLogMode.FULL);
            assertThat(props.getEndpoints().get(1).getPattern()).isEqualTo("/actuator/**");
            assertThat(props.getEndpoints().get(1).getMode()).isEqualTo(HttpLogMode.OFF);
        });
    }

}
