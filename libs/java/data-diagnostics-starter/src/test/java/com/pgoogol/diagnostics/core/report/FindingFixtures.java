package com.pgoogol.diagnostics.core.report;

import com.pgoogol.diagnostics.core.CallSite;
import com.pgoogol.diagnostics.core.DataAccessEventFixtures;
import com.pgoogol.diagnostics.core.OperationKind;
import com.pgoogol.diagnostics.core.UnitOfWorkSummary;
import com.pgoogol.diagnostics.core.UnitOfWorkType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Wnioski i jednostki do testów wyjść: test podaje to, co sprawdza, resztę wypełniają sensowne wartości. */
public final class FindingFixtures {

    public static final Instant START = Instant.parse("2026-10-03T12:00:00Z");

    public static final Instant END = START.plusMillis(890);

    private FindingFixtures() {
    }

    /** Zamknięta jednostka HTTP z 52 operacjami i 612 ms w bazie. */
    public static UnitOfWorkSummary unit(String name) {

        Duration database = Duration.ofMillis(612);
        return new UnitOfWorkSummary("a1b2c3d4", name, UnitOfWorkType.HTTP, "6e1b9f", 52, database, START, END);
    }

    /** Kształt odczytu wykonany 37 razy, łącznie 412 ms, najdłużej 30 ms. */
    public static ShapeSummary shape(String shape, CallSite... callers) {

        Duration total = Duration.ofMillis(412);
        Duration max = Duration.ofMillis(30);
        List<CallSite> callerList = List.of(callers);
        return new ShapeSummary(DataAccessEventFixtures.POSTGRESQL, OperationKind.READ, shape, false, shape,
            37, 0, total, max, callerList);
    }

    /** Wniosek WARN o jednym kształcie, pomiar 37 przy progu 5. */
    public static Finding shapeFinding(String code, ShapeSummary shape) {

        List<ShapeSummary> shapes = List.of(shape);
        return new Finding(code, Severity.WARN, "Ten sam odczyt wykonany 37 razy", 37, 5, shapes);
    }

    public static CallSite caller(String method, int line) {

        return new CallSite("com.example.OrderService", method, line, "OrderRepository.findAllByStatus");
    }
}
