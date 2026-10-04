package com.pgoogol.testdiagnostics.core;

import java.util.List;
import java.util.Objects;

/**
 * Niezmienny stan przebiegu w chwili raportu. Raport liczy wszystko z migawki, więc
 * da się go sprawdzić na migawce złożonej ręcznie, bez uruchamiania testów.
 *
 * @param wallMillis     czas przebiegu od otwarcia sesji testów
 * @param classes        klasy w kolejności startu
 * @param testTimings    czas każdego testu
 * @param environments   nowe środowiska w kolejności startu
 * @param resumeCount    wznowienia wstrzymanych środowisk z pamięci podręcznej Springa
 * @param resumeMillis   łączny czas tych wznowień
 * @param failures       błędy testów i całych klas w kolejności wystąpienia
 * @param classFailures  klasy, które padły w całości
 * @param memory         pamięć i GC na koniec przebiegu
 */
public record TestRunSnapshot(
    long wallMillis,
    List<ClassRecord> classes,
    List<TestTiming> testTimings,
    List<EnvironmentStart> environments,
    int resumeCount,
    long resumeMillis,
    List<FailureRecord> failures,
    int classFailures,
    MemorySnapshot memory) {

    public TestRunSnapshot {

        classes = List.copyOf(classes);
        testTimings = List.copyOf(testTimings);
        environments = List.copyOf(environments);
        failures = List.copyOf(failures);
        Objects.requireNonNull(memory, "migawka pamięci jest wymagana");
    }

    public int tests() {

        return classes.stream().mapToInt(ClassRecord::tests).sum();
    }

    public int failed() {

        return classes.stream().mapToInt(ClassRecord::failed).sum();
    }

    public int skipped() {

        return classes.stream().mapToInt(ClassRecord::skipped).sum();
    }

    public int passed() {

        return tests() - failed() - skipped();
    }

    /** Błędy testów plus klasy, które padły w całości. */
    public int problems() {

        return failed() + classFailures;
    }

    public long testMillis() {

        return classes.stream().mapToLong(ClassRecord::testMillis).sum();
    }

    public long environmentStartMillis() {

        return environments.stream().mapToLong(EnvironmentStart::durationMillis).sum();
    }

    /** Starty nowych środowisk razem z wznowieniami wstrzymanych. */
    public long environmentMillis() {

        return environmentStartMillis() + resumeMillis;
    }

    /** Czas poza testami i środowiskami: przygotowanie i sprzątanie klas, wykrywanie testów. */
    public long otherMillis() {

        return Math.max(0, wallMillis - testMillis() - environmentMillis());
    }
}
