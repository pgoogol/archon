package com.pgoogol.testdiagnostics;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Granice startera: cały kod we własnej przestrzeni nazw, a rdzeń i raport bez JUnit
 * i Springa. Moduł bez {@code spring-test} też ma dostać raport, a rejestrator ma się
 * dać sprawdzić bez uruchamiania testów.
 */
class ArchitectureTest {

    private static final String BASE = "com.pgoogol.testdiagnostics";

    private static JavaClasses moduleClasses;

    @BeforeAll
    static void importClasses() {

        // katalog wyjściowy kompilacji zamiast pakietu: import po pakiecie nie
        // zobaczyłby klasy, która przez pomyłkę wylądowała poza com.pgoogol
        Path mainClasses = Path.of("target", "classes");
        moduleClasses = new ClassFileImporter().importPath(mainClasses);
    }

    @Test
    @DisplayName("każda klasa startera siedzi pod com.pgoogol.testdiagnostics")
    void allClasses_liveUnderLibraryBasePackage() {

        // given
        ArchRule rule = classes()
            .should().resideInAPackage(BASE + "..")
            .because("biblioteka z libs/java ma jedną własną przestrzeń nazw pod com.pgoogol");

        // when & then
        rule.check(moduleClasses);
    }

    @Test
    @DisplayName("rdzeń i raport nie znają JUnit ani Springa")
    void coreAndReport_stayFreeOfTestFrameworks() {

        // given: słuchacze JUnit i Springa podają rdzeniowi gotowe dane; gdyby rdzeń
        // sięgał po ich typy, moduł bez spring-test nie załadowałby raportu
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(BASE + ".core..", BASE + ".report..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.junit..", "org.springframework..")
            .because("rdzeń ma działać w każdym module z testami, także bez Springa");

        // when & then
        rule.check(moduleClasses);
    }

    @Test
    @DisplayName("słuchacze JUnit nie znają Springa")
    void junitAdapter_staysFreeOfSpring() {

        // given: spring-test jest opcjonalny, a słuchacze JUnit ładują się w każdym module
        ArchRule rule = noClasses()
            .that().resideInAPackage(BASE + ".junit..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .because("moduł bez Springa też ma dostać raport");

        // when & then
        rule.check(moduleClasses);
    }
}
