package com.pgoogol.testdiagnostics.report;

import com.pgoogol.testdiagnostics.core.Share;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ReportFormatTest {

    private final ReportFormat format = new ReportFormat("com.example.");

    @ParameterizedTest(name = "{0} ms → {1}")
    @CsvSource({
        "0,       0.0 s",
        "1250,    1.3 s",
        "59900,   59.9 s",
        "60000,   1 min 00 s",
        "125000,  2 min 05 s"
    })
    @DisplayName("czas poniżej minuty w sekundach z jedną cyfrą po kropce, od minuty w minutach i sekundach")
    void duration_formatsSecondsThenMinutes(long millis, String expected) {

        // when
        String duration = format.duration(millis);

        // then
        assertThat(duration).isEqualTo(expected);
    }

    @Test
    @DisplayName("pasek ma zawsze 20 znaków, a pusta całość daje pusty pasek")
    void bar_isTwentyCharactersLong() {

        // when
        String half = format.bar(new Share(50, 100));
        String none = format.bar(new Share(5, 0));
        String overfull = format.bar(new Share(300, 100));

        // then
        assertAll(
            () -> assertThat(half).isEqualTo("##########.........."),
            () -> assertThat(none).isEqualTo("...................."),
            () -> assertThat(overfull).isEqualTo("####################"));
    }

    @Test
    @DisplayName("za długi tekst traci początek, bo koniec nazwy klasy mówi najwięcej; pusty to kreska")
    void fit_trimsFromLeft() {

        // when
        String trimmed = format.fit("order.api.OrderControllerIntegrationTest", 20);
        String empty = format.fit("", 20);
        String fitting = format.fit("OrderTest", 20);

        // then
        assertAll(
            () -> assertThat(trimmed).isEqualTo("...erIntegrationTest").hasSize(20),
            () -> assertThat(empty).isEqualTo("-"),
            () -> assertThat(fitting).isEqualTo("OrderTest"));
    }

    @Test
    @DisplayName("wspólny pakiet to najdłuższy wspólny początek pakietów wszystkich klas")
    void forClasses_findsCommonPackage() {

        // given
        List<String> classes = List.of(
            "com.example.order.OrderApiTest",
            "com.example.order.api.OrderControllerTest",
            "com.example.payment.PaymentTest");

        // when
        ReportFormat common = ReportFormat.forClasses(classes);

        // then
        assertAll(
            () -> assertThat(common.packagePrefix()).isEqualTo("com.example."),
            () -> assertThat(common.shortName("com.example.order.OrderApiTest")).isEqualTo("order.OrderApiTest"));
    }

    @Test
    @DisplayName("klasa bez pakietu albo brak klas daje pusty prefiks i pełne nazwy")
    void forClasses_whenNoCommonPackage_keepsFullNames() {

        // when
        ReportFormat withDefaultPackage = ReportFormat.forClasses(List.of("com.example.OrderTest", "PlainTest"));
        ReportFormat empty = ReportFormat.forClasses(List.of());

        // then
        assertAll(
            () -> assertThat(withDefaultPackage.packagePrefix()).isEmpty(),
            () -> assertThat(empty.packagePrefix()).isEmpty(),
            () -> assertThat(withDefaultPackage.shortName("com.example.OrderTest")).isEqualTo("com.example.OrderTest"));
    }
}
