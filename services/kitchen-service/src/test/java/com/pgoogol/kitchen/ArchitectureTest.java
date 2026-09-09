package com.pgoogol.kitchen;

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
 * Egzekucja granic tego serwisu: reguł z .claude/rules/backend-build.md oraz
 * kierunku zależności opisanego w docs/ARCHITEKTURA.md. Nazwa serwisu jest
 * wpisana świadomie — to test JEGO granic, nie szkielet do kopiowania.
 *
 * <p>Część modułów (recipe, revision, imports, media) dochodzi w kolejnych
 * kamieniach. Reguły, których zbiór klas jest dziś pusty, mają
 * {@code allowEmptyShould(true)} — zaczną gryźć w chwili, gdy pakiet powstanie,
 * zamiast wywalać build za to, że jeszcze go nie ma.</p>
 */
class ArchitectureTest {

    private static final String BASE = "com.pgoogol.kitchen";

    /** Obce pakiety, po które serwis może sięgać: startery z libs/java. */
    private static final String LOGGING_STARTER = "com.pgoogol.httpexchangelogger";

    /** Warstwa LLM ze startera — wchodzi razem z importem przepisów. */
    private static final String LLM_STARTER = "com.pgoogol.llm";

    private static JavaClasses classesUnderTest;

    @BeforeAll
    static void importClasses() {

        classesUnderTest = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.pgoogol");
    }

    @Test
    @DisplayName("każda klasa serwisu siedzi pod com.pgoogol.kitchen")
    void allClasses_liveUnderServiceBasePackage() {

        // given
        ArchRule rule = classes()
            .that().resideOutsideOfPackages(LOGGING_STARTER + "..", LLM_STARTER + "..")
            .should().resideInAPackage(BASE + "..")
            .because("pakiet bezpośrednio pod com.pgoogol kolidowałby z kolejnym serwisem");

        // when & then
        rule.check(classesUnderTest);
    }

    @Test
    @DisplayName("serwis nie zależy od innego serwisu")
    void service_doesNotDependOnAnotherService() {

        // given
        DescribedPredicate<JavaClass> foreignDomain = resideInAPackage("com.pgoogol..")
            .and(resideOutsideOfPackages(BASE + "..", LOGGING_STARTER + "..", LLM_STARTER + ".."))
            .as("klasa z innej domeny com.pgoogol");

        ArchRule rule = noClasses()
            .that().resideInAPackage(BASE + "..")
            .should().dependOnClassesThat(foreignDomain)
            .because("zależności wewnątrz reaktora prowadzą wyłącznie do libs/java");

        // when & then
        rule.check(classesUnderTest);
    }

    @Test
    @DisplayName("kontrolery REST mieszkają wyłącznie w pakiecie api")
    void controllers_liveOnlyInApiPackage() {

        // given
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage(BASE + ".api..")
            .because("kontrolery, DTO i mappery trzymamy w api");

        // when & then
        rule.check(classesUnderTest);
    }

    @Test
    @DisplayName("nic spoza api nie sięga po api")
    void domain_doesNotDependOnApi() {

        // given
        ArchRule rule = noClasses()
            .that().resideOutsideOfPackages(BASE + ".api..")
            .should().dependOnClassesThat().resideInAPackage(BASE + ".api..")
            .because("zależność idzie od api w głąb domeny, nigdy odwrotnie");

        // when & then
        rule.check(classesUnderTest);
    }

    @Test
    @DisplayName("domena nie wie o warstwie aplikacji ani o infrastrukturze")
    void domain_doesNotDependOnOuterLayers() {

        // given: rdzeń zna wyłącznie siebie — port wychodzący jest interfejsem
        // w domain, a jego implementacja siedzi w infrastructure
        ArchRule rule = noClasses()
            .that().resideInAPackage(BASE + "..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(BASE + "..application..", BASE + "..infrastructure..")
            .because("zależności prowadzą do środka, nigdy na zewnątrz");

        // when & then
        rule.check(classesUnderTest);
    }

    @Test
    @DisplayName("encje JPA mieszkają w domain")
    void entities_liveInDomainPackages() {

        // given
        ArchRule rule = classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().resideInAPackage(BASE + "..domain..")
            .because("model utrwalany jest modelem domenowym tego serwisu")
            .allowEmptyShould(true);

        // when & then
        rule.check(classesUnderTest);
    }

    @Test
    @DisplayName("repozytoria Spring Data mieszkają w infrastructure")
    void repositories_liveInInfrastructurePackages() {

        // given
        ArchRule rule = classes()
            .that().areAssignableTo("org.springframework.data.repository.Repository")
            .and().resideInAPackage(BASE + "..")
            .should().resideInAPackage(BASE + "..infrastructure..")
            .because("repozytorium jest adapterem wyjściowym, nie częścią domeny")
            .allowEmptyShould(true);

        // when & then
        rule.check(classesUnderTest);
    }

    @Test
    @DisplayName("przepis nie wie o imporcie")
    void recipe_doesNotDependOnImports() {

        // given: kierunek jest jednokierunkowy — import wie o przepisie, przepis
        // o imporcie nie; inaczej każda zmiana w parsowaniu ruszałaby rdzeń
        ArchRule rule = noClasses()
            .that().resideInAPackage(BASE + ".recipe..")
            .should().dependOnClassesThat().resideInAPackage(BASE + ".imports..")
            .because("import woła przepis, nigdy odwrotnie")
            .allowEmptyShould(true);

        // when & then
        rule.check(classesUnderTest);
    }

    @Test
    @DisplayName("po warstwę LLM sięga wyłącznie import i kanał czatów")
    void llm_staysInsideImportAndInbox() {

        // given: LLM jest szczegółem importu, a nie sposobem pracy serwisu —
        // wpuszczony do rdzenia zamienia każdą operację w wywołanie płatnego API
        ArchRule rule = noClasses()
            .that().resideOutsideOfPackages(BASE + ".imports..", BASE + ".inbox..")
            .should().dependOnClassesThat().resideInAPackage(LLM_STARTER + "..")
            .because("warstwa AI wchodzi przez import, nie przez cały serwis");

        // when & then
        rule.check(classesUnderTest);
    }

    @Test
    @DisplayName("przeliczanie jednostek nie zna Springa ani JPA")
    void unitConversion_staysFreeOfFrameworks() {

        // given: zamiana szklanek na mililitry to arytmetyka — sprawdza się ją
        // przez podanie liczb, a nie przez postawienie kontekstu
        ArchRule rule = noClasses()
            .that().resideInAPackage(BASE + ".dictionary.units..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..")
            .because("konwersja jednostek ma się dać testować jak zwykły kod");

        // when & then
        rule.check(classesUnderTest);
    }

    @Test
    @DisplayName("silnik rewizji nie zna Springa, JPA ani bazy")
    void revisionEngine_staysFreeOfFrameworks() {

        // given: cofanie zmian i liczenie różnic sprawdza się przez podanie
        // dwóch stanów, a nie przez zapisanie czegokolwiek
        ArchRule rule = noClasses()
            .that().resideInAPackage(BASE + ".revision.diff..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..")
            .because("dziennik zmian ma się dać testować jak zwykły kod")
            .allowEmptyShould(true);

        // when & then
        rule.check(classesUnderTest);
    }

    @Test
    @DisplayName("po system plików sięga wyłącznie infrastruktura mediów")
    void fileSystem_isTouchedOnlyByMediaInfrastructure() {

        // given: jedno miejsce dotykające dysku znaczy jedno miejsce do zmiany,
        // gdy pliki przeniosą się do magazynu obiektowego
        ArchRule rule = noClasses()
            .that().resideOutsideOfPackages(BASE + ".media.infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("java.nio.file..")
            .because("magazyn plików jest adapterem, nie techniką rozsianą po serwisie");

        // when & then
        rule.check(classesUnderTest);
    }

    @Test
    @DisplayName("wstrzykiwanie wyłącznie przez konstruktor")
    void fields_areNotAutowired() {

        // given
        ArchRule rule = noFields()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .because("reguła stylu: wstrzykiwanie konstruktorem, nigdy @Autowired na polu");

        // when & then
        rule.check(classesUnderTest);
    }
}
