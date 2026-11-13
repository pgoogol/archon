package com.pgoogol.diagnostics.it;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Cztery sposoby prowadzenia transakcji, które diagnostyka ma rozróżnić. */
@Component
public class TransactionScenarios {

    private final OrderRepository orders;

    private final JdbcTemplate jdbcTemplate;

    private final TransactionTemplate transactionTemplate;

    private final AuditWriter auditWriter;

    public TransactionScenarios(OrderRepository orders, JdbcTemplate jdbcTemplate,
                                TransactionTemplate transactionTemplate, AuditWriter auditWriter) {

        this.orders = orders;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.auditWriter = auditWriter;
    }

    /** Same odczyty bez {@code readOnly}: kandydat na transakcję tylko do odczytu. */
    @Transactional
    public long declarativeRead() {

        return orders.count();
    }

    public void programmaticWrite() {

        transactionTemplate.executeWithoutResult(status -> insert("T-1"));
    }

    @Transactional
    public void rolledBack() {

        insert("R-1");
        throw new IllegalStateException("wycofanie na potrzeby testu");
    }

    /** Zewnętrzna transakcja czyta, wewnętrzna {@code REQUIRES_NEW} zapisuje. */
    @Transactional
    public void outerWithRequiresNew() {

        orders.count();
        auditWriter.recordInNewTransaction("N-1");
    }

    private void insert(String number) {

        jdbcTemplate.update("insert into orders (number) values (?)", number);
    }
}
