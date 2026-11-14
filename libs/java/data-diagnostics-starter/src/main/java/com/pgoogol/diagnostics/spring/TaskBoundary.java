package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.UnitOfWorkBoundary;
import com.pgoogol.diagnostics.core.UnitOfWorkHandoff;
import com.pgoogol.diagnostics.core.UnitOfWorkScope;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import org.springframework.core.task.TaskDecorator;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Granica zadań wykonywanych poza wątkiem żądania: {@code @Async} i {@code @Scheduled}.
 * Boot podpina każdy bean {@link TaskDecorator} do wykonawcy zadań i do harmonogramu,
 * a kilka dekoratorów składa w jeden, więc ta klasa nie wypiera dekoratora aplikacji.
 *
 * <p>Zadanie zlecone z wątku, który ma otwartą jednostkę (zwykle {@code @Async} z żądania),
 * dołącza do niej: jego zapytania liczą się w jednostce rodzica, a ta zamyka się dopiero
 * po zadaniu. Gdy rodzic zdążył się zamknąć albo go nie było, zadanie dostaje własną
 * jednostkę: {@code scheduled} o nazwie {@code Klasa.metoda} dla metody {@code @Scheduled},
 * {@code async} dla pozostałych.</p>
 */
public class TaskBoundary implements TaskDecorator, UnitOfWorkBoundary {

    static final String ASYNC_NAME = "async";

    /**
     * Zadanie metody {@code @Scheduled} przedstawia się jako {@code pakiet.Klasa.metoda}.
     * Harmonogram podaje je dekoratorowi opakowane, a opis opakowania kończy się na
     * {@code … for pakiet.Klasa.metoda]]}, więc szukamy tej nazwy na końcu opisu. Spring nie
     * daje tu innego dostępu do metody; gdy opis się zmieni, zadanie dostanie jednostkę
     * {@code async} zamiast {@code scheduled}, bez innych skutków.
     */
    private static final Pattern SCHEDULED_METHOD =
        Pattern.compile("(?:^|for )(?:[\\w$]+\\.)+([\\w$]+)\\.(\\w+)]*$");

    private final Supplier<DiagnosticsEngine> engine;

    public TaskBoundary(Supplier<DiagnosticsEngine> engine) {

        this.engine = Objects.requireNonNull(engine, "dostęp do silnika jest wymagany");
    }

    /** Typ jednostek zadań bez metody {@code @Scheduled}; zadania harmonogramu mają {@code scheduled}. */
    @Override
    public UnitOfWorkType type() {

        return UnitOfWorkType.ASYNC;
    }

    @Override
    public Runnable decorate(Runnable task) {

        DiagnosticsEngine current = engine.get();
        Optional<UnitOfWorkHandoff> parent = current.handoff();
        String description = String.valueOf(task);
        return () -> run(task, description, parent);
    }

    private void run(Runnable task, String description, Optional<UnitOfWorkHandoff> parent) {

        DiagnosticsEngine current = engine.get();
        try (UnitOfWorkScope ignored = parent.flatMap(current::join).orElseGet(() -> open(current, description))) {

            task.run();
        }
    }

    private UnitOfWorkScope open(DiagnosticsEngine current, String description) {

        Matcher scheduled = SCHEDULED_METHOD.matcher(description);
        if (scheduled.find()) {

            String className = scheduled.group(1);
            String simpleName = className.substring(className.lastIndexOf('$') + 1);
            String name = simpleName + "." + scheduled.group(2);
            return current.open(name, UnitOfWorkType.SCHEDULED);
        }
        return current.open(ASYNC_NAME, UnitOfWorkType.ASYNC);
    }
}
