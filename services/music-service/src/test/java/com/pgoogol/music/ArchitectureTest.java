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

import java.util.List;
import java.util.stream.Stream;

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

    /** Jedyne obce pakiety, po które serwis może sięgać: startery z libs/java. */
    private static final List<String> LIB_PACKAGES = List.of(
        "com.pgoogol.httpexchangelogger",
        "com.pgoogol.bearerauth"
    );

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
            .that().resideOutsideOfPackages(libPackages())
            .should().resideInAPackage(BASE + "..")
            .because("pakiet bezpośrednio pod com.pgoogol kolidowałby z kolejnym serwisem");

        // when / then
        rule.check(classesUnderTest);
    }

    @Test
    @DisplayName("serwis nie zależy od innego serwisu")
    void service_doesNotDependOnAnotherService() {

        // given
        DescribedPredicate<JavaClass> foreignDomain = resideInAPackage("com.pgoogol..")
            .and(resideOutsideOfPackages(serviceAndLibPackages()))
            .as("klasa z innej domeny com.pgoogol");

        ArchRule rule = noClasses()
            .that().resideInAPackage(BASE + "..")
            .should().dependOnClassesThat(foreignDomain)
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
            .and().resideInAPackage(BASE + "..")
            .should().resideInAPackage(BASE + ".api..")
            .because("kontrolery, DTO i mappery trzymamy w api");

        // when / then
        rule.check(classesUnderTest);
    }

    @Test
    @DisplayName("kod domenowy nie sięga po kontrolery")
    void domain_doesNotDependOnControllers() {

        // given
        ArchRule rule = noClasses()
            .that().resideOutsideOfPackage(BASE + ".api..")
            .and().resideInAPackage(BASE + "..")
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

    private static String[] libPackages() {

        return LIB_PACKAGES.stream()
            .map(name -> name + "..")
            .toArray(String[]::new);
    }

    private static String[] serviceAndLibPackages() {

        Stream<String> own = Stream.of(BASE + "..");
        Stream<String> libs = LIB_PACKAGES.stream().map(name -> name + "..");
        return Stream.concat(own, libs).toArray(String[]::new);
    }
}
