package com.pgoogol.httpexchangelogger.runtime;

import com.pgoogol.httpexchangelogger.autoconfigure.HttpExchangeLoggerProperties;
import com.pgoogol.httpexchangelogger.model.HttpLogMode;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class RuntimeModeOverrideManagerTest {

    private final MutableClock clock = new MutableClock();

    private RuntimeModeOverrideManager manager(boolean requireTtlForFull) {

        HttpExchangeLoggerProperties properties = new HttpExchangeLoggerProperties();
        properties.setRequireTtlForFullLogging(requireTtlForFull);
        return new RuntimeModeOverrideManager(properties, clock, new AntPathMatcher());
    }

    @Test
    void resolve_whenGlobalOverrideSet_appliesToEveryPath() {

        // given
        RuntimeModeOverrideManager manager = manager(false);
        manager.setGlobalOverride(HttpLogMode.FULL, null);

        // when / then
        assertThat(manager.resolve("/api/orders")).contains(HttpLogMode.FULL);
        assertThat(manager.resolve("/actuator/health")).contains(HttpLogMode.FULL);
    }

    @Test
    void resolve_whenNoOverrideSet_returnsEmpty() {

        // given
        RuntimeModeOverrideManager manager = manager(false);

        // when / then
        assertThat(manager.resolve("/api/orders")).isEmpty();
    }

    @Test
    void resolve_whenPatternOverrideMatches_returnsItsMode() {

        // given
        RuntimeModeOverrideManager manager = manager(false);
        manager.setPatternOverride("/api/orders/**", HttpLogMode.FULL, null);

        // when / then
        assertThat(manager.resolve("/api/orders/123")).contains(HttpLogMode.FULL);
        assertThat(manager.resolve("/api/products")).isEmpty();
    }

    @Test
    void resolve_whenPatternAndGlobalOverridesPresent_patternWins() {

        // given
        RuntimeModeOverrideManager manager = manager(false);
        manager.setGlobalOverride(HttpLogMode.BASIC, null);
        manager.setPatternOverride("/api/orders/**", HttpLogMode.OFF, null);

        // when / then
        assertThat(manager.resolve("/api/orders/123")).contains(HttpLogMode.OFF);
        assertThat(manager.resolve("/api/products")).contains(HttpLogMode.BASIC);
    }

    @Test
    void resolve_whenOverrideTtlElapsed_overrideExpires() {

        // given
        RuntimeModeOverrideManager manager = manager(false);
        manager.setGlobalOverride(HttpLogMode.FULL, Duration.ofMinutes(10));

        // when
        clock.advance(Duration.ofMinutes(11));

        // then
        assertThat(manager.resolve("/api/orders")).isEmpty();
        assertThat(manager.getActiveOverrides()).isEmpty();
    }

    @Test
    void resolve_whenOverrideTtlNotElapsed_overrideStillActive() {

        // given
        RuntimeModeOverrideManager manager = manager(false);
        manager.setGlobalOverride(HttpLogMode.FULL, Duration.ofMinutes(10));

        // when
        clock.advance(Duration.ofMinutes(9));

        // then
        assertThat(manager.resolve("/api/orders")).contains(HttpLogMode.FULL);
    }

    @Test
    void setGlobalOverride_whenRequireTtlAndFullWithoutTtl_throwsIllegalArgument() {

        // given
        RuntimeModeOverrideManager manager = manager(true);

        // when
        Throwable thrown = catchThrowable(() -> manager.setGlobalOverride(HttpLogMode.FULL, null));

        // then
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("require-ttl-for-full-logging");
    }

    @Test
    void setGlobalOverride_whenRequireTtlAndFullWithTtl_accepts() {

        // given
        RuntimeModeOverrideManager manager = manager(true);

        // when
        manager.setGlobalOverride(HttpLogMode.FULL, Duration.ofMinutes(5));

        // then
        assertThat(manager.resolve("/api/orders")).contains(HttpLogMode.FULL);
    }

    @Test
    void setGlobalOverride_whenRequireTtlAndNonFullWithoutTtl_accepts() {

        // given
        RuntimeModeOverrideManager manager = manager(true);

        // when
        manager.setGlobalOverride(HttpLogMode.LIMITED, null);

        // then
        assertThat(manager.resolve("/api/orders")).contains(HttpLogMode.LIMITED);
    }

    @Test
    void setPatternOverride_whenSamePatternSetTwice_replacesPreviousOverride() {

        // given
        RuntimeModeOverrideManager manager = manager(false);
        manager.setPatternOverride("/api/orders/**", HttpLogMode.FULL, null);

        // when
        manager.setPatternOverride("/api/orders/**", HttpLogMode.BASIC, null);

        // then
        assertThat(manager.resolve("/api/orders/1")).contains(HttpLogMode.BASIC);
        assertThat(manager.getActiveOverrides()).hasSize(1);
    }

    @Test
    void setGlobalOverride_whenTtlNotPositive_throwsIllegalArgument() {

        // given
        RuntimeModeOverrideManager manager = manager(false);

        // when
        Throwable thrown = catchThrowable(() -> manager.setGlobalOverride(HttpLogMode.FULL, Duration.ZERO));

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setPatternOverride_whenPatternBlank_throwsIllegalArgument() {

        // given
        RuntimeModeOverrideManager manager = manager(false);

        // when
        Throwable thrown = catchThrowable(() -> manager.setPatternOverride(" ", HttpLogMode.FULL, null));

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clearAll_whenOverridesPresent_removesEverything() {

        // given
        RuntimeModeOverrideManager manager = manager(false);
        manager.setGlobalOverride(HttpLogMode.FULL, null);
        manager.setPatternOverride("/api/**", HttpLogMode.OFF, null);

        // when
        manager.clearAll();

        // then
        assertThat(manager.getActiveOverrides()).isEmpty();
        assertThat(manager.resolve("/api/orders")).isEmpty();
    }

    @Test
    void clearPatternOverride_whenPatternGiven_removesOnlyThatOverride() {

        // given
        RuntimeModeOverrideManager manager = manager(false);
        manager.setGlobalOverride(HttpLogMode.BASIC, null);
        manager.setPatternOverride("/api/**", HttpLogMode.FULL, null);

        // when
        manager.clearPatternOverride("/api/**");

        // then
        assertThat(manager.resolve("/api/orders")).contains(HttpLogMode.BASIC);
    }

    private static class MutableClock extends Clock {

        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public ZoneId getZone() {

            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {

            return this;
        }

        @Override
        public Instant instant() {

            return instant;
        }

        void advance(Duration duration) {

            instant = instant.plus(duration);
        }

    }

}
