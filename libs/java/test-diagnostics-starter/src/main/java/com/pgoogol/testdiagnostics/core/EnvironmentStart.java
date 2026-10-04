package com.pgoogol.testdiagnostics.core;

import java.util.List;
import java.util.Objects;

/**
 * Nowe środowisko testowe: kontekst Springa uruchomiony dla klasy testów, bo żaden
 * z kontekstów w pamięci podręcznej nie pasował do jej konfiguracji.
 *
 * @param testClassName  klasa, dla której kontekst wystartował (może być {@code @Nested})
 * @param durationMillis czas startu, także nieudanego
 * @param profiles       aktywne profile; puste, gdy start się nie udał
 * @param beanCount      liczba definicji beanów; 0, gdy start się nie udał
 * @param failed         start zakończony wyjątkiem
 */
public record EnvironmentStart(
    String testClassName,
    long durationMillis,
    List<String> profiles,
    int beanCount,
    boolean failed) {

    public EnvironmentStart {

        Objects.requireNonNull(testClassName, "nazwa klasy testów jest wymagana");
        profiles = List.copyOf(profiles);
    }

    public static EnvironmentStart started(String testClassName, long durationMillis, List<String> profiles, int beanCount) {

        return new EnvironmentStart(testClassName, durationMillis, profiles, beanCount, false);
    }

    public static EnvironmentStart failed(String testClassName, long durationMillis) {

        return new EnvironmentStart(testClassName, durationMillis, List.of(), 0, true);
    }
}
