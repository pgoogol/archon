package com.pgoogol.diagnostics.it;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.LongStream;

/** Scenariusze, w których problemem jest praca Hibernate, a nie liczba zapytań. */
@Component
public class HibernateScenarios {

    private final OrderRepository orders;

    private final OrderItemRepository items;

    private final ProductRepository products;

    private final EntityManager entityManager;

    private final JdbcTemplate jdbcTemplate;

    public HibernateScenarios(OrderRepository orders, OrderItemRepository items, ProductRepository products,
                              EntityManager entityManager, JdbcTemplate jdbcTemplate) {

        this.orders = orders;
        this.items = items;
        this.products = products;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Jawny flush po każdym odczycie: dwanaście flushy w jednej transakcji. */
    @Transactional
    public void flushInLoop() {

        LongStream.rangeClosed(1, 12).forEach(index -> {

            orders.count();
            entityManager.flush();
        });
    }

    /** Ładuje wszystkie zamówienia i pozycje, a potem flushuje: Hibernate sprawdza zmiany w każdej encji. */
    @Transactional
    public int loadEverythingAndFlush() {

        List<Order> allOrders = orders.findAll();
        List<OrderItem> allItems = items.findAll();
        entityManager.flush();
        return allOrders.size() + allItems.size();
    }

    /** Dokłada trzy tysiące pozycji zwykłym SQL-em, żeby było co ładować i sprawdzać. */
    @Transactional
    public void seedManyItems() {

        jdbcTemplate.update("""
            insert into order_item (order_id, sku, quantity)
            select 1, 'bulk-' || n, 1 from generate_series(1, 3000) as n""");
    }

    /** Każdy produkt czytany raz, każdy w osobnej sesji: cache drugiego poziomu tylko chybia. */
    public void readEachProductOnce() {

        LongStream.rangeClosed(1, 25).forEach(products::findById);
    }
}
