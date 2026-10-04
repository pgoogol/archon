package com.pgoogol.testdiagnostics.junit.fixture;

import org.junit.jupiter.api.condition.EnabledIf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Klasa-wzorzec dla testów słuchaczy; działa tylko w zagnieżdżonym uruchomieniu, patrz {@link FixtureRun}. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@EnabledIf("com.pgoogol.testdiagnostics.junit.fixture.FixtureRun#active")
public @interface FixtureCases {
}
