package com.pgoogol.music;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * Egzekucja reguł z .claude/rules/backend-build.md i backend-codestyle.md.
 * Nazwa serwisu jest wpisana świadomie — to test JEGO granic, nie szkielet
 * do kopiowania między serwisami.
 */
class ArchitectureTest {

    private static final String BASE = "com.pgoogol.music";

    /** Jedyny obcy pakiet, po który serwis może sięgać: starter z libs/java. */
    private static final String LOGGING_STARTER = "com.pgoogol.httpexchangelogger";

    private static JavaClasses classesUnderTest;

    @BeforeAll
    static void importClasses() {

        classesUnderTest = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.pgoogol");
    }

    @Test
    @DisplayName("każda klasa serwisu siedzi pod com.pgoogol.music")
    void allClasses_liveUnderServiceBasePackage() {

        // given
        ArchRule rule = classes()
            .that().resideOutsideOfPackage(LOGGING_STARTER + "..")
            .should().resideInAPackage(BASE + "..")
            .because("pakiet bezpośrednio pod com.pgoogol kolidowałby z kolejnym serwisem");

        // when / then
        rule.check(classesUnderTest);
    }

    @Test
    @DisplayName("serwis nie zależy od innego serwisu")
    void service_doesNotDependOnAnotherService() {

        // given
        DescribedPredicate<JavaClass> obcaDomena = resideInAPackage("com.pgoogol..")
            .and(resideOutsideOfPackages(BASE + "..", LOGGING_STARTER + ".."))
            .as("klasa z innej domeny com.pgoogol");

        ArchRule rule = noClasses()
            .that().resideInAPackage(BASE + "..")
            .should().dependOnClassesThat(obcaDomena)
            .because("zależności wewnątrz reaktora prowadzą wyłącznie do libs/java");

        // when / then
        rule.check(classesUnderTest);
    }

    @Test
    @DisplayName("kontrolery REST mieszkają wyłącznie w pakiecie api")
    void controllers_liveOnlyInApiPackage() {

        // given
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage(BASE + ".api..")
            .because("kontrolery, DTO i mappery trzymamy w api (D1)");

        // when / then
        rule.check(classesUnderTest);
    }

    @Test
    @DisplayName("kod domenowy nie sięga po kontrolery")
    void domain_doesNotDependOnControllers() {

        // given
        ArchRule rule = noClasses()
            .that().resideOutsideOfPackage(BASE + ".api..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Controller")
            .because("zależność idzie od api w głąb domeny, nigdy odwrotnie");

        // when / then
        rule.check(classesUnderTest);
    }

    @Test
    @DisplayName("wstrzykiwanie wyłącznie przez konstruktor")
    void fields_areNotAutowired() {

        // given
        ArchRule rule = noFields()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .because("reguła stylu: wstrzykiwanie konstruktorem, nigdy @Autowired na polu");

        // when / then
        rule.check(classesUnderTest);
    }
}
