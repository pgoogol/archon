package com.pgoogol.diagnostics.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

class DiagnosticsSettingsTest {

    @Test
    @DisplayName("dev trzyma pełny tekst, listę 10 000 zdarzeń i miejsce wywołania każdej operacji")
    void defaults_whenDev_collectsEverythingForDeveloper() {

        // when
        DiagnosticsSettings settings = DiagnosticsSettings.defaults(DiagnosticsMode.DEV);

        // then
        assertAll(
            () -> assertThat(settings.keepStatementText()).isTrue(),
            () -> assertThat(settings.captureParameters()).isFalse(),
            () -> assertThat(settings.unitEventLimit()).isEqualTo(10_000),
            () -> assertThat(settings.unitShapeLimit()).isEqualTo(500),
            () -> assertThat(settings.callSiteCapture()).isEqualTo(CallSiteCapture.EVERY_OPERATION),
            () -> assertThat(settings.captureOutsideUnit()).isFalse());
    }

    @Test
    @DisplayName("prod zostawia sam kształt i liczniki, a miejsce wywołania tylko przy problemie")
    void defaults_whenProd_keepsOnlyShapeAndCounters() {

        // when
        DiagnosticsSettings settings = DiagnosticsSettings.defaults(DiagnosticsMode.PROD);

        // then
        assertAll(
            () -> assertThat(settings.keepStatementText()).isFalse(),
            () -> assertThat(settings.captureParameters()).isFalse(),
            () -> assertThat(settings.unitEventLimit()).isZero(),
            () -> assertThat(settings.unitShapeLimit()).isEqualTo(200),
            () -> assertThat(settings.callSiteCapture()).isEqualTo(CallSiteCapture.SLOW_OR_REPEATED),
            () -> assertThat(settings.captureOutsideUnit()).isFalse());
    }

    @Test
    @DisplayName("w dev parametry zapytań da się włączyć")
    void withCaptureParameters_whenDev_enablesParameters() {

        // given
        DiagnosticsSettings settings = DiagnosticsSettings.defaults(DiagnosticsMode.DEV);

        // when
        DiagnosticsSettings changed = settings.withCaptureParameters(true);

        // then
        assertThat(changed.captureParameters()).isTrue();
    }

    @Test
    @DisplayName("włączenie parametrów w prod jest błędem konfiguracji, nie cichą poprawką")
    void withCaptureParameters_whenProd_fails() {

        // given
        DiagnosticsSettings settings = DiagnosticsSettings.defaults(DiagnosticsMode.PROD);

        // when & then
        assertThatIllegalArgumentException()
            .isThrownBy(() -> settings.withCaptureParameters(true))
            .withMessageContaining("parametry");
    }

    @Test
    @DisplayName("lista zdarzeń w prod jest błędem konfiguracji")
    void withUnitEventLimit_whenProd_fails() {

        // given
        DiagnosticsSettings settings = DiagnosticsSettings.defaults(DiagnosticsMode.PROD);

        // when & then
        assertThatIllegalArgumentException()
            .isThrownBy(() -> settings.withUnitEventLimit(100))
            .withMessageContaining("nie trzyma zdarzeń");
    }

    @Test
    @DisplayName("wyłączona analiza przestaje pracować, a ponowne włączenie ją przywraca")
    void withAnalyzerEnabled_whenToggled_switchesOnlyThatAnalyzer() {

        // given
        DiagnosticsSettings settings = DiagnosticsSettings.defaults(DiagnosticsMode.DEV);

        // when
        DiagnosticsSettings disabled = settings.withAnalyzerEnabled("n-plus-one", false);
        DiagnosticsSettings enabledAgain = disabled.withAnalyzerEnabled("n-plus-one", true);

        // then
        assertAll(
            () -> assertThat(settings.analyzerEnabled("n-plus-one")).isTrue(),
            () -> assertThat(disabled.analyzerEnabled("n-plus-one")).isFalse(),
            () -> assertThat(disabled.analyzerEnabled("slow-operation")).isTrue(),
            () -> assertThat(enabledAgain.analyzerEnabled("n-plus-one")).isTrue());
    }

    @Test
    @DisplayName("limit kształtów musi zostawić miejsce choć na jeden kształt")
    void withUnitShapeLimit_whenZero_fails() {

        // given
        DiagnosticsSettings settings = DiagnosticsSettings.defaults(DiagnosticsMode.DEV);

        // when & then
        assertThatIllegalArgumentException()
            .isThrownBy(() -> settings.withUnitShapeLimit(0))
            .withMessageContaining("limit kształtów");
    }
}
