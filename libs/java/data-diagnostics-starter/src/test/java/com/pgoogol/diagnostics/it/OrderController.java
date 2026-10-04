package com.pgoogol.diagnostics.it;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint na każdy sposób dociągania pozycji; wszystkie mają ten sam wzorzec trasy. */
@RestController
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {

        this.service = service;
    }

    @GetMapping("/orders/items/{way}")
    public int items(@PathVariable String way) {

        return switch (way) {

            case "repository" -> service.byRepository();
            case "jpql" -> service.byJpql();
            case "criteria" -> service.byCriteria();
            case "native" -> service.byNativeQuery();
            case "jdbc-template" -> service.byJdbcTemplate();
            case "lazy" -> service.byLazyLoading();
            default -> throw new IllegalArgumentException("nieznany sposób: " + way);
        };
    }
}
