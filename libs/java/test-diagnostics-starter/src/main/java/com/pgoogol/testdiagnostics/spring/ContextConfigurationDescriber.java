package com.pgoogol.testdiagnostics.spring;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.io.support.PropertySourceDescriptor;
import org.springframework.test.context.BootstrapUtils;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContextAnnotationUtils;
import org.springframework.test.context.TestContextBootstrapper;
import org.springframework.test.context.bean.override.BeanOverrideHandler;
import org.springframework.test.context.web.WebMergedContextConfiguration;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Opisuje konfigurację kontekstu klasy testów tak, jak widzi ją pamięć podręczna
 * Springa: buduje {@link MergedContextConfiguration} publicznym API i zamienia ją na
 * {@link ContextDescription}. Woła się go tylko przy nowym środowisku.
 *
 * <p>Dwa dostosowania opisuje szczegółowo, bo to najczęstsze źródła nowych środowisk:
 * nadpisania beanów ({@code @MockitoBean}, {@code @MockitoSpyBean}, {@code @TestBean})
 * i metody {@code @DynamicPropertySource}. Nadpisanie opisuje tak, jak porównuje je
 * Spring: typ i nazwa beanu albo nazwa pola, bez klasy, w której pole leży. Pozostałe
 * dostosowania dostają nazwę klasy z numerem wariantu.</p>
 *
 * <p>Wartości właściwości z kluczem zawierającym {@code password}, {@code secret},
 * {@code token} albo {@code key} raport pokazuje jako {@code ***} z krótkim odciskiem,
 * żeby dwie różne wartości nie wyglądały na równe.</p>
 */
final class ContextConfigurationDescriber {

    private static final String BEAN_OVERRIDE_CUSTOMIZER =
        "org.springframework.test.context.bean.override.BeanOverrideContextCustomizer";

    private static final String DYNAMIC_PROPERTIES_CUSTOMIZER =
        "org.springframework.test.context.support.DynamicPropertiesContextCustomizer";

    private final PropertyMasker propertyMasker = new PropertyMasker();

    Described describe(Class<?> testClass, EnvironmentRegistry registry) {

        try {

            TestContextBootstrapper bootstrapper = BootstrapUtils.resolveTestContextBootstrapper(testClass);
            MergedContextConfiguration merged = bootstrapper.buildMergedContextConfiguration();
            ContextDescription description = describe(merged, registry);
            return Described.of(description);
        } catch (RuntimeException | LinkageError failure) {

            String message = Objects.toString(failure.getMessage(), "");
            String firstLine = message.lines().findFirst().orElse("");
            return Described.failed(failure.getClass().getSimpleName() + ": " + firstLine);
        }
    }

    ContextDescription describe(MergedContextConfiguration merged, EnvironmentRegistry registry) {

        Map<String, List<String>> attributes = new LinkedHashMap<>();
        attributes.put(ContextDescription.CLASSES, classNames(merged.getClasses()));
        attributes.put(ContextDescription.LOCATIONS, List.of(merged.getLocations()));
        attributes.put(ContextDescription.PROFILES, List.of(merged.getActiveProfiles()));
        attributes.put(ContextDescription.PROPERTY_SOURCES, propertySources(merged));
        attributes.put(ContextDescription.PROPERTIES, properties(merged));
        attributes.put(ContextDescription.BEAN_OVERRIDES, beanOverrides(merged));
        attributes.put(ContextDescription.DYNAMIC_PROPERTIES, dynamicProperties(merged));
        attributes.put(ContextDescription.CUSTOMIZERS, otherCustomizers(merged, registry));
        attributes.put(ContextDescription.INITIALIZERS, initializers(merged));
        attributes.put(ContextDescription.LOADER, loader(merged));
        attributes.put(ContextDescription.WEB, web(merged));
        attributes.put(ContextDescription.PARENT, parent(merged, registry));
        return new ContextDescription(attributes);
    }

    private static List<String> classNames(Class<?>[] classes) {

        return Stream.of(classes).map(Class::getName).toList();
    }

    private static List<String> propertySources(MergedContextConfiguration merged) {

        // @TestPropertySource z samymi właściwościami daje źródło bez plików; właściwości
        // opisuje osobny atrybut, a puste źródło dawałoby w raporcie samą kreskę
        return merged.getPropertySourceDescriptors().stream()
            .map(PropertySourceDescriptor::locations)
            .filter(locations -> !locations.isEmpty())
            .map(locations -> String.join(",", locations))
            .toList();
    }

    private List<String> properties(MergedContextConfiguration merged) {

        return Stream.of(merged.getPropertySourceProperties())
            .map(propertyMasker::mask)
            .toList();
    }

    private static List<String> beanOverrides(MergedContextConfiguration merged) {

        if (!hasCustomizer(merged, BEAN_OVERRIDE_CUSTOMIZER)) {

            return List.of();
        }
        return searchedClasses(merged.getTestClass()).stream()
            .flatMap(testClass -> BeanOverrideHandler.forTestClass(testClass).stream())
            .map(ContextConfigurationDescriber::beanOverride)
            .distinct()
            .sorted()
            .toList();
    }

    /** {@code @MockitoBean OrderRepository orderRepository}: adnotacja, typ, nazwa beanu albo pola. */
    private static String beanOverride(BeanOverrideHandler handler) {

        String handlerName = handler.getClass().getSimpleName();
        String annotation = "@" + handlerName.replace("OverrideHandler", "");
        Class<?> beanType = handler.getBeanType().toClass();
        String text = annotation + " " + beanType.getSimpleName();
        String beanName = handler.getBeanName();
        if (Objects.nonNull(beanName)) {

            text = text + " '" + beanName + "'";
        } else if (Objects.nonNull(handler.getField())) {

            text = text + " " + handler.getField().getName();
        }
        if (!handler.getContextName().isEmpty()) {

            text = text + " [" + handler.getContextName() + "]";
        }
        return text;
    }

    private static List<String> dynamicProperties(MergedContextConfiguration merged) {

        if (!hasCustomizer(merged, DYNAMIC_PROPERTIES_CUSTOMIZER)) {

            return List.of();
        }
        return searchedClasses(merged.getTestClass()).stream()
            .flatMap(testClass -> dynamicPropertyMethods(testClass).stream())
            .map(method -> method.getDeclaringClass().getName() + "." + method.getName())
            .distinct()
            .sorted()
            .toList();
    }

    private static Set<Method> dynamicPropertyMethods(Class<?> testClass) {

        return MethodIntrospector.selectMethods(testClass,
            (Method method) -> MergedAnnotations.from(method).isPresent(DynamicPropertySource.class));
    }

    private static List<String> otherCustomizers(MergedContextConfiguration merged, EnvironmentRegistry registry) {

        return merged.getContextCustomizers().stream()
            .filter(Predicate.not(customizer -> isNamed(customizer, BEAN_OVERRIDE_CUSTOMIZER)))
            .filter(Predicate.not(customizer -> isNamed(customizer, DYNAMIC_PROPERTIES_CUSTOMIZER)))
            .map(registry::variantOf)
            .sorted()
            .toList();
    }

    private static List<String> initializers(MergedContextConfiguration merged) {

        return merged.getContextInitializerClasses().stream()
            .map(Class::getName)
            .sorted()
            .toList();
    }

    private static List<String> loader(MergedContextConfiguration merged) {

        Object contextLoader = merged.getContextLoader();
        if (Objects.isNull(contextLoader)) {

            return List.of();
        }
        Class<?> loaderClass = contextLoader.getClass();
        return List.of(loaderClass.getSimpleName());
    }

    private static List<String> web(MergedContextConfiguration merged) {

        if (merged instanceof WebMergedContextConfiguration webConfiguration) {

            return List.of(webConfiguration.getResourceBasePath());
        }
        return List.of();
    }

    /** Kontekst nadrzędny ({@code @ContextHierarchy}) jako jedna wartość: jego atrybuty w skrócie. */
    private List<String> parent(MergedContextConfiguration merged, EnvironmentRegistry registry) {

        MergedContextConfiguration parent = merged.getParent();
        if (Objects.isNull(parent)) {

            return List.of();
        }
        ContextDescription description = describe(parent, registry);
        List<String> parts = description.attributes().entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
            .toList();
        return List.of(String.join("; ", parts));
    }

    /** Klasa testów i klasy zewnętrzne, w których Spring szuka konfiguracji klasy {@code @Nested}. */
    private static List<Class<?>> searchedClasses(Class<?> testClass) {

        return Stream.<Class<?>>iterate(testClass, Objects::nonNull, ContextConfigurationDescriber::enclosingSearched)
            .toList();
    }

    private static @Nullable Class<?> enclosingSearched(Class<?> testClass) {

        if (TestContextAnnotationUtils.searchEnclosingClass(testClass)) {

            return testClass.getEnclosingClass();
        }
        return null;
    }

    private static boolean hasCustomizer(MergedContextConfiguration merged, String className) {

        return merged.getContextCustomizers().stream().anyMatch(customizer -> isNamed(customizer, className));
    }

    private static boolean isNamed(ContextCustomizer customizer, String className) {

        Class<?> customizerClass = customizer.getClass();
        return Objects.equals(customizerClass.getName(), className);
    }
}
