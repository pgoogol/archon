package com.pgoogol.diagnostics.it;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint na każdy scenariusz transakcji. */
@RestController
public class TransactionController {

    private final TransactionScenarios scenarios;

    public TransactionController(TransactionScenarios scenarios) {

        this.scenarios = scenarios;
    }

    @GetMapping("/transactions/{way}")
    public String run(@PathVariable String way) {

        switch (way) {

            case "declarative" -> scenarios.declarativeRead();
            case "programmatic" -> scenarios.programmaticWrite();
            case "rollback" -> rollbackQuietly();
            case "requires-new" -> scenarios.outerWithRequiresNew();
            case "hold" -> scenarios.holdConnectionWhileWaiting();
            default -> throw new IllegalArgumentException("nieznany scenariusz: " + way);
        }
        return way;
    }

    private void rollbackQuietly() {

        try {

            scenarios.rolledBack();
        } catch (IllegalStateException expected) {

            // wyjątek jest częścią scenariusza: ma wycofać transakcję, nie zepsuć żądania
        }
    }
}
