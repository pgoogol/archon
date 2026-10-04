package com.pgoogol.testdiagnostics.spring;

import com.pgoogol.testdiagnostics.core.AttributeDifference;
import com.pgoogol.testdiagnostics.core.EnvironmentCause;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentCauseResolverTest {

    private final EnvironmentCauseResolver resolver = new EnvironmentCauseResolver();

    @Test
    @DisplayName("bez wcześniejszych środowisk to pierwsze środowisko")
    void resolve_whenNothingKnown_isFirst() {

        // when
        EnvironmentCause cause = resolver.resolve(description("test"), List.of(), Map.of());

        // then
        assertThat(cause).isEqualTo(EnvironmentCause.first());
    }

    @Test
    @DisplayName("ta sama konfiguracja co wcześniej: najnowsze równe środowisko i klasa, która je zamknęła")
    void resolve_whenSameAsEarlier_isReloadedFromLatestEqual() {

        // given
        List<KnownEnvironment> known = List.of(
            new KnownEnvironment(1, description("test")),
            new KnownEnvironment(2, description("other")),
            new KnownEnvironment(3, description("test")));

        // when
        EnvironmentCause cause = resolver.resolve(description("test"), known, Map.of(3, "com.example.DirtyTest"));

        // then
        assertThat(cause).isEqualTo(new EnvironmentCause.Reloaded(3, "com.example.DirtyTest"));
    }

    @Test
    @DisplayName("ta sama konfiguracja bez śladu @DirtiesContext: wypadła przez limit pamięci podręcznej")
    void resolve_whenSameWithoutCloser_isReloadedWithoutDirtiesContext() {

        // when
        EnvironmentCause cause = resolver.resolve(description("test"),
            List.of(new KnownEnvironment(1, description("test"))), Map.of());

        // then
        assertThat(cause).isInstanceOfSatisfying(EnvironmentCause.Reloaded.class,
            reloaded -> assertThat(reloaded.closedByDirtiesContext()).isFalse());
    }

    @Test
    @DisplayName("inna konfiguracja: różnice względem najbliższego środowiska, z dodanymi i usuniętymi wartościami")
    void resolve_whenDifferent_listsDifferencesAgainstNearest() {

        // given: #1 różni się profilem i mockiem, #2 tylko mockiem
        List<KnownEnvironment> known = List.of(
            new KnownEnvironment(1, description("local")),
            new KnownEnvironment(2, description("test", "@MockitoBean Clock clock")));

        // when
        EnvironmentCause cause = resolver.resolve(description("test", "@MockitoBean Mailer mailer"), known, Map.of());

        // then
        assertThat(cause).isEqualTo(new EnvironmentCause.Differs(2, List.of(
            new AttributeDifference(ContextDescription.BEAN_OVERRIDES,
                List.of("@MockitoBean Mailer mailer"), List.of("@MockitoBean Clock clock")))));
    }

    @Test
    @DisplayName("przy remisie odległości wygrywa wcześniejsze środowisko")
    void resolve_whenTie_picksEarlier() {

        // given
        List<KnownEnvironment> known = List.of(
            new KnownEnvironment(1, description("alpha")),
            new KnownEnvironment(2, description("beta")));

        // when
        EnvironmentCause cause = resolver.resolve(description("gamma"), known, Map.of());

        // then
        assertThat(cause).isInstanceOfSatisfying(EnvironmentCause.Differs.class,
            differs -> assertThat(differs.environment()).isEqualTo(1));
    }

    private static ContextDescription description(String profile, String... beanOverrides) {

        return new ContextDescription(Map.of(
            ContextDescription.PROFILES, List.of(profile),
            ContextDescription.BEAN_OVERRIDES, List.of(beanOverrides)));
    }
}
