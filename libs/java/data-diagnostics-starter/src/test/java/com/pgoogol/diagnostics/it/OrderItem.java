package com.pgoogol.diagnostics.it;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Pozycja zamówienia testowej aplikacji. */
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    private String sku;

    private int quantity;

    protected OrderItem() {
    }

    public Long getId() {

        return id;
    }

    public Order getOrder() {

        return order;
    }

    public String getSku() {

        return sku;
    }

    public int getQuantity() {

        return quantity;
    }
}
