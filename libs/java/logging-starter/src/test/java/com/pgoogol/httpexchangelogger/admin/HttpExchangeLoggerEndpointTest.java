package com.pgoogol.httpexchangelogger.admin;

import com.pgoogol.httpexchangelogger.admin.HttpExchangeLoggerEndpoint.HttpExchangeLoggerStatus;
import com.pgoogol.httpexchangelogger.autoconfigure.HttpExchangeLoggerProperties;
import com.pgoogol.httpexchangelogger.model.HttpLogMode;
import com.pgoogol.httpexchangelogger.runtime.RuntimeModeOverrideManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.InvalidEndpointRequestException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class HttpExchangeLoggerEndpointTest {

    private final HttpExchangeLoggerProperties properties = new HttpExchangeLoggerProperties();
    private final RuntimeModeOverrideManager overrideManager = new RuntimeModeOverrideManager(properties);
    private final HttpExchangeLoggerEndpoint endpoint = new HttpExchangeLoggerEndpoint(properties, overrideManager);

    @Test
    void status_whenNoOverrides_reportsConfigurationAndEmptyOverrideList() {

        // given / when
        HttpExchangeLoggerStatus status = endpoint.status();

        // then
        assertThat(status.enabled()).isTrue();
        assertThat(status.defaultMode()).isEqualTo(HttpLogMode.BASIC);
        assertThat(status.samplingRate()).isEqualTo(1.0d);
        assertThat(status.activeOverrides()).isEmpty();
    }

    @Test
    void setOverride_whenNoPatternGiven_setsGlobalOverride() {

        // when
        HttpExchangeLoggerStatus status = endpoint.setOverride("FULL", null, 600L);

        // then
        assertThat(status.activeOverrides()).hasSize(1);
        assertThat(status.activeOverrides().getFirst().isGlobal()).isTrue();
        assertThat(overrideManager.resolve("/api/orders")).contains(HttpLogMode.FULL);
    }

    @Test
    void setOverride_whenPatternGiven_setsPatternOverride() {

        // when
        endpoint.setOverride("off", "/actuator/**", null);

        // then mode parsing is case-insensitive and only matching paths are affected
        assertThat(overrideManager.resolve("/actuator/health")).contains(HttpLogMode.OFF);
        assertThat(overrideManager.resolve("/api/orders")).isEmpty();
    }

    @Test
    void setOverride_whenModeUnknown_throwsInvalidEndpointRequest() {

        // when
        Throwable thrown = catchThrowable(() -> endpoint.setOverride("VERBOSE", null, null));

        // then
        assertThat(thrown).isInstanceOf(InvalidEndpointRequestException.class);
    }

    @Test
    void setOverride_whenRequireTtlViolated_throwsInvalidEndpointRequest() {

        // given
        properties.setRequireTtlForFullLogging(true);

        // when
        Throwable thrown = catchThrowable(() -> endpoint.setOverride("FULL", null, null));

        // then
        assertThat(thrown).isInstanceOf(InvalidEndpointRequestException.class);
    }

    @Test
    void clearOverrides_whenPatternGiven_removesOnlyMatchingOverride() {

        // given
        endpoint.setOverride("FULL", "/api/**", null);
        endpoint.setOverride("BASIC", null, null);

        // when
        HttpExchangeLoggerStatus status = endpoint.clearOverrides("/api/**");

        // then
        assertThat(status.activeOverrides()).hasSize(1);
        assertThat(status.activeOverrides().getFirst().isGlobal()).isTrue();
    }

    @Test
    void clearOverrides_whenNoPatternGiven_removesAllOverrides() {

        // given
        endpoint.setOverride("FULL", "/api/**", null);
        endpoint.setOverride("BASIC", null, null);

        // when
        HttpExchangeLoggerStatus status = endpoint.clearOverrides(null);

        // then
        assertThat(status.activeOverrides()).isEmpty();
    }

}
