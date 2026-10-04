package com.pgoogol.testdiagnostics.spring;

import com.pgoogol.testdiagnostics.spring.fixture.ConflictingConfigurationCases;
import com.pgoogol.testdiagnostics.spring.fixture.DescribedDynamicCases;
import com.pgoogol.testdiagnostics.spring.fixture.DescribedPropertiesCases;
import com.pgoogol.testdiagnostics.spring.fixture.OtherConfig;
import com.pgoogol.testdiagnostics.spring.fixture.OverrideAPlainCases;
import com.pgoogol.testdiagnostics.spring.fixture.OverrideBMockCases;
import com.pgoogol.testdiagnostics.spring.fixture.SharedFirstCases;
import com.pgoogol.testdiagnostics.spring.fixture.SharedSecondCases;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/** Opis konfiguracji bez ładowania kontekstu: tylko {@code MergedContextConfiguration}. */
class ContextConfigurationDescriberTest {

    private final ContextConfigurationDescriber describer = new ContextConfigurationDescriber();

    private final EnvironmentRegistry registry = new EnvironmentRegistry();

    @Test
    @DisplayName("dwie klasy, które Spring obsłuży jednym kontekstem, mają równe opisy")
    void describe_whenSameConfiguration_givesEqualDescriptions() {

        // when
        ContextDescription first = description(SharedFirstCases.class);
        ContextDescription second = description(SharedSecondCases.class);

        // then
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("klasy konfiguracji, profile i właściwości trafiają do opisu, hasło bez wartości")
    void describe_listsClassesProfilesAndMaskedProperties() {

        // when
        ContextDescription description = description(DescribedPropertiesCases.class);

        // then
        assertAll(
            () -> assertThat(description.values(ContextDescription.CLASSES)).containsExactly(OtherConfig.class.getName()),
            () -> assertThat(description.values(ContextDescription.PROFILES)).containsExactly("alpha"),
            () -> assertThat(description.values(ContextDescription.PROPERTIES))
                .hasSize(2)
                .contains("app.mode=fast")
                .noneMatch(property -> property.contains("s3cret")));
    }

    @Test
    @DisplayName("@MockitoBean opisany jak w porównaniu Springa: adnotacja, typ i pole")
    void describe_listsBeanOverrides() {

        // when
        ContextDescription plain = description(OverrideAPlainCases.class);
        ContextDescription mocked = description(OverrideBMockCases.class);

        // then
        assertAll(
            () -> assertThat(plain.values(ContextDescription.BEAN_OVERRIDES)).isEmpty(),
            () -> assertThat(mocked.values(ContextDescription.BEAN_OVERRIDES)).containsExactly("@MockitoBean Greeter greeter"));
    }

    @Test
    @DisplayName("metoda @DynamicPropertySource opisana klasą i nazwą")
    void describe_listsDynamicPropertySources() {

        // when
        ContextDescription description = description(DescribedDynamicCases.class);

        // then
        assertThat(description.values(ContextDescription.DYNAMIC_PROPERTIES))
            .containsExactly(DescribedDynamicCases.class.getName() + ".properties");
    }

    @Test
    @DisplayName("konfiguracja, której Spring nie zbuduje, nie przerywa testów: opis ma błąd zamiast wyjątku")
    void describe_whenBootstrapFails_returnsFailure() {

        // when
        Described described = describer.describe(ConflictingConfigurationCases.class, registry);

        // then
        assertAll(
            () -> assertThat(described.description()).isEmpty(),
            () -> assertThat(described.failure()).isNotBlank());
    }

    private ContextDescription description(Class<?> testClass) {

        Described described = describer.describe(testClass, registry);
        return described.description().orElseThrow(() -> new AssertionError(described.failure()));
    }
}
