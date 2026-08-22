package com.pgoogol.finance.category.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.lang.Nullable;

import java.util.Objects;

/**
 * Kategoria wydatku lub przychodu; dwa poziomy w praktyce, ale struktura
 * jest rekurencyjna.
 */
@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // jawne LAZY: @ManyToOne domyślnie ładuje się zachłannie i przy drzewie
    // kategorii oznaczałoby zapytanie na każdego rodzica
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CategoryDirection direction;

    @Column(nullable = false)
    private boolean archived;

    protected Category() {

    }

    public Category(@Nullable Category parent, String name, CategoryDirection direction) {

        moveTo(parent, name, direction);
    }

    public void moveTo(@Nullable Category parent, String name, CategoryDirection direction) {

        this.parent = parent;
        this.name = Objects.requireNonNull(name, "name");
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    /** Archiwizacja zamiast usunięcia — inaczej historyczne transakcje tracą przypisanie. */
    public void archive() {

        this.archived = true;
    }

    public Long getId() {

        return id;
    }

    @Nullable
    public Category getParent() {

        return parent;
    }

    @Nullable
    public Long getParentId() {

        if (Objects.isNull(parent)) {

            return null;
        }
        return parent.getId();
    }

    public String getName() {

        return name;
    }

    public CategoryDirection getDirection() {

        return direction;
    }

    public boolean isArchived() {

        return archived;
    }
}
