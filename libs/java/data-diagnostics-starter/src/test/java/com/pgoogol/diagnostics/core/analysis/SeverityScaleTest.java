package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.report.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SeverityScaleTest {

    @Test
    @DisplayName("pomiar poniżej progu nie daje wniosku")
    void grade_whenBelowThreshold_returnsEmpty() {

        // given
        SeverityScale scale = SeverityScale.of(5);

        // when
        Optional<Severity> severity = scale.grade(4);

        // then
        assertThat(severity).isEmpty();
    }

    @ParameterizedTest(name = "pomiar {0} przy progu 5 → {1}")
    @CsvSource({"5, WARN", "24, WARN", "25, CRITICAL", "1000, CRITICAL"})
    @DisplayName("od progu jest WARN, od pięciokrotności progu CRITICAL")
    void grade_whenAtOrAboveThreshold_usesDefaultMultiplier(long measured, Severity expected) {

        // given
        SeverityScale scale = SeverityScale.of(5);

        // when
        Optional<Severity> severity = scale.grade(measured);

        // then
        assertThat(severity).contains(expected);
    }

    @Test
    @DisplayName("mnożnik z ustawień analizy przesuwa granicę CRITICAL")
    void grade_whenCustomMultiplier_movesCriticalBoundary() {

        // given
        SeverityScale scale = new SeverityScale(100, 2);

        // when
        Optional<Severity> severity = scale.grade(200);

        // then
        assertThat(severity).contains(Severity.CRITICAL);
    }

    @Test
    @DisplayName("olbrzymi próg nie przepełnia granicy CRITICAL")
    void criticalThreshold_whenProductOverflows_saturates() {

        // given
        SeverityScale scale = SeverityScale.of(Long.MAX_VALUE / 2);

        // when
        long critical = scale.criticalThreshold();

        // then
        assertThat(critical).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("próg 0 dawałby wniosek z każdej jednostki, więc jest odrzucany")
    void constructor_whenThresholdZero_fails() {

        // when & then
        assertThatIllegalArgumentException()
            .isThrownBy(() -> SeverityScale.of(0))
            .withMessageContaining("próg");
    }
}
