package com.pgoogol.diagnostics.it;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Sześć sposobów na ten sam błąd: pętla po zamówieniach, która dla każdego osobno
 * dociąga pozycje. Każda metoda daje jedno zapytanie o zamówienia i po jednym o pozycje
 * na zamówienie, więc diagnostyka ma wskazać ją jako miejsce wywołania wniosku N+1.
 */
@Service
public class OrderService {

    private static final String ITEMS_JPQL = "select i from OrderItem i where i.order.id = :orderId";

    private static final String ITEMS_SQL = "select * from order_item where order_id = :orderId";

    private static final String ITEMS_JDBC = "select id, sku, quantity from order_item where order_id = ?";

    private final OrderRepository orders;

    private final OrderItemRepository items;

    private final EntityManager entityManager;

    private final JdbcTemplate jdbcTemplate;

    public OrderService(OrderRepository orders, OrderItemRepository items, EntityManager entityManager,
                        JdbcTemplate jdbcTemplate) {

        this.orders = orders;
        this.items = items;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    public int byRepository() {

        return allOrders().stream()
            .mapToInt(order -> items.findByOrderId(order.getId()).size())
            .sum();
    }

    public int byJpql() {

        return allOrders().stream()
            .mapToInt(order -> entityManager.createQuery(ITEMS_JPQL, OrderItem.class)
                .setParameter("orderId", order.getId())
                .getResultList()
                .size())
            .sum();
    }

    public int byCriteria() {

        return allOrders().stream()
            .mapToInt(order -> itemsByCriteria(order).size())
            .sum();
    }

    public int byNativeQuery() {

        return allOrders().stream()
            .mapToInt(order -> entityManager.createNativeQuery(ITEMS_SQL, OrderItem.class)
                .setParameter("orderId", order.getId())
                .getResultList()
                .size())
            .sum();
    }

    public int byJdbcTemplate() {

        return allOrders().stream()
            .mapToInt(order -> jdbcTemplate.queryForList(ITEMS_JDBC, order.getId()).size())
            .sum();
    }

    /** Pozycje dociąga Hibernate przy pierwszym dotknięciu kolekcji, w otwartej transakcji. */
    @Transactional(readOnly = true)
    public int byLazyLoading() {

        return allOrders().stream()
            .mapToInt(order -> order.getItems().size())
            .sum();
    }

    private List<Order> allOrders() {

        return orders.findAll();
    }

    /** Wywołanie przez metodę pomocniczą: miejscem wywołania jest ta metoda, nie publiczna. */
    private List<OrderItem> itemsByCriteria(Order order) {

        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<OrderItem> query = builder.createQuery(OrderItem.class);
        Root<OrderItem> item = query.from(OrderItem.class);
        query.where(builder.equal(item.get("order").get("id"), order.getId()));
        return entityManager.createQuery(query).getResultList();
    }
}
