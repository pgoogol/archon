package com.pgoogol.diagnostics.it;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/** Produkt testowej aplikacji, trzymany w cache drugiego poziomu. */
@Entity
@Table(name = "product")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Product {

    @Id
    private Long id;

    private String name;

    protected Product() {
    }

    public Long getId() {

        return id;
    }

    public String getName() {

        return name;
    }
}
