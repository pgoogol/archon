package com.pgoogol.testdiagnostics.spring;

import com.pgoogol.testdiagnostics.core.EnvironmentCause;
import com.pgoogol.testdiagnostics.core.EnvironmentStart;
import com.pgoogol.testdiagnostics.core.TestRunRecorder;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextCustomizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Środowiska jednego przebiegu: numery z raportu, opisy konfiguracji, które klasa
 * zamknęła które środowisko, i warianty dostosowań kontekstu.
 *
 * <p>Spring tworzy słuchacza osobno dla każdej klasy testów, więc rejestr wisi na
 * przebiegu ({@link TestRunRecorder#attachment}). Konteksty trzyma słabo: zamknięte
 * przez {@code @DirtiesContext} mogą zniknąć z pamięci. Numer, powód i zapis startu
 * w rejestratorze idą pod jedną blokadą, żeby przy klasach wykonywanych równolegle
 * numer zgadzał się z miejscem w tabeli raportu.</p>
 */
final class EnvironmentRegistry {

    private final ReentrantLock lock = new ReentrantLock();

    private final EnvironmentCauseResolver resolver = new EnvironmentCauseResolver();

    private final List<KnownEnvironment> known = new ArrayList<>();

    private final Map<ApplicationContext, Integer> numbers = new WeakHashMap<>();

    private final Map<Integer, String> closedBy = new HashMap<>();

    private final List<ContextCustomizer> customizers = new ArrayList<>();

    private int count;

    static EnvironmentRegistry of(TestRunRecorder recorder) {

        return recorder.attachment(EnvironmentRegistry.class, EnvironmentRegistry::new);
    }

    /**
     * Zapisuje start środowiska z powodem liczonym względem wcześniejszych.
     *
     * @param context kontekst po udanym starcie; {@code null} po nieudanym
     * @return numer środowiska w raporcie
     */
    int record(TestRunRecorder recorder, Described described, Function<EnvironmentCause, EnvironmentStart> start,
               @Nullable ApplicationContext context) {

        lock.lock();
        try {

            EnvironmentCause cause = cause(described);
            EnvironmentStart environment = start.apply(cause);
            count++;
            int number = count;
            if (Objects.nonNull(context)) {

                numbers.put(context, number);
                described.description().ifPresent(description -> known.add(new KnownEnvironment(number, description)));
            }
            recorder.environmentStarted(environment);
            return number;
        } finally {

            lock.unlock();
        }
    }

    Optional<Integer> numberOf(ApplicationContext context) {

        lock.lock();
        try {

            Integer number = numbers.get(context);
            return Optional.ofNullable(number);
        } finally {

            lock.unlock();
        }
    }

    /** Środowisko zniknęło z pamięci podręcznej po klasie testów, czyli przez {@code @DirtiesContext}. */
    void closed(int number, String testClassName) {

        lock.lock();
        try {

            closedBy.put(number, testClassName);
        } finally {

            lock.unlock();
        }
    }

    /**
     * Nazwa dostosowania kontekstu z numerem wariantu: równe ({@code equals}) dostają ten
     * sam numer. Większość dostosowań nie ma czytelnego {@code toString}, a różnica
     * wariantu to wszystko, co Spring o nich wie przy porównaniu konfiguracji.
     */
    String variantOf(ContextCustomizer customizer) {

        lock.lock();
        try {

            List<ContextCustomizer> sameType = customizers.stream()
                .filter(seen -> Objects.equals(seen.getClass(), customizer.getClass()))
                .toList();
            int index = sameType.indexOf(customizer);
            if (index < 0) {

                customizers.add(customizer);
                index = sameType.size();
            }
            String name = customizer.getClass().getSimpleName();
            return name + " #" + (index + 1);
        } finally {

            lock.unlock();
        }
    }

    private EnvironmentCause cause(Described described) {

        if (described.description().isEmpty()) {

            return new EnvironmentCause.Unknown(described.failure());
        }
        ContextDescription description = described.description().get();
        return resolver.resolve(description, known, closedBy);
    }
}
