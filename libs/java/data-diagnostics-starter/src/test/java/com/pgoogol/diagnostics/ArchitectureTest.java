package com.pgoogol.diagnostics;

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
 * Granice startera: cały kod we własnej przestrzeni nazw, a rdzeń bez frameworków
 * i sterowników. Rdzeń ma się dać uruchomić i przetestować bez kontekstu Springa
 * i bez bazy — strategie przechwytywania podpinają się do niego z zewnątrz.
 */
class ArchitectureTest {

    private static final String BASE = "com.pgoogol.diagnostics";

    private static JavaClasses moduleClasses;

    @BeforeAll
    static void importClasses() {

        // katalog wyjściowy kompilacji zamiast pakietu: import po pakiecie nie
        // zobaczyłby klasy, która przez pomyłkę wylądowała poza com.pgoogol
        Path mainClasses = Path.of("target", "classes");
        moduleClasses = new ClassFileImporter().importPath(mainClasses);
    }

    @Test
    @DisplayName("każda klasa startera siedzi pod com.pgoogol.diagnostics")
    void allClasses_liveUnderLibraryBasePackage() {

        // given
        ArchRule rule = classes()
            .should().resideInAPackage(BASE + "..")
            .because("biblioteka z libs/java ma jedną własną przestrzeń nazw pod com.pgoogol");

        // when & then
        rule.check(moduleClasses);
    }

    @Test
    @DisplayName("rdzeń nie zna Springa, JPA, Hibernate ani datasource-proxy")
    void core_staysFreeOfFrameworks() {

        // given: rdzeń zna wyłącznie interfejsy strategii, a konkretny mechanizm
        // przechwytywania żyje poza nim — inaczej każda nowa baza albo framework
        // wymuszałyby zmianę w silniku i analizach
        ArchRule rule = noClasses()
            .that().resideInAPackage(BASE + ".core..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "net.ttddyy..",
                "org.hibernate..")
            .because("rdzeń ma się dać testować jak zwykły kod, bez kontekstu i bez bazy");

        // when & then
        rule.check(moduleClasses);
    }
}
