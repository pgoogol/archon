package com.pgoogol.diagnostics.it;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint na każdy scenariusz pracy Hibernate. */
@RestController
public class HibernateController {

    private final HibernateScenarios scenarios;

    public HibernateController(HibernateScenarios scenarios) {

        this.scenarios = scenarios;
    }

    @GetMapping("/hibernate/{way}")
    public String run(@PathVariable String way) {

        switch (way) {

            case "flush-loop" -> scenarios.flushInLoop();
            case "seed" -> scenarios.seedManyItems();
            case "load-all" -> scenarios.loadEverythingAndFlush();
            case "cache-misses" -> scenarios.readEachProductOnce();
            default -> throw new IllegalArgumentException("nieznany scenariusz: " + way);
        }
        return way;
    }
}
