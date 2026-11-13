package com.pgoogol.diagnostics.it;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Zapis w osobnej transakcji; osobny bean, bo wywołanie w obrębie klasy ominęłoby proxy. */
@Component
public class AuditWriter {

    private final JdbcTemplate jdbcTemplate;

    public AuditWriter(JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInNewTransaction(String number) {

        jdbcTemplate.update("insert into orders (number) values (?)", number);
    }
}
