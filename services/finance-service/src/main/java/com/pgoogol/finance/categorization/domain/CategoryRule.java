package com.pgoogol.finance.categorization.domain;

import com.pgoogol.finance.category.domain.Category;
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

import java.util.Objects;

/**
 * Reguła podpowiadająca kategorię dla wiersza wyciągu.
 *
 * <p>Dopasowanie jest <b>zawieraniem tekstu</b>, nie wyrażeniem regularnym.
 * Regexp wpisany przez pomyłkę potrafi dopasować wszystko albo nic, a błąd
 * widać dopiero po zaimportowaniu wyciągu.</p>
 */
@Entity
@Table(name = "category_rule")
public class CategoryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String pattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_field", nullable = false, length = 20)
    private MatchField matchField;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** Niższa liczba wygrywa; przy równej rozstrzyga identyfikator. */
    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean active;

    protected CategoryRule() {

    }

    public CategoryRule(String pattern, MatchField matchField, Category category, int priority) {

        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.matchField = Objects.requireNonNull(matchField, "matchField");
        this.category = Objects.requireNonNull(category, "category");
        this.priority = priority;
        this.active = true;
    }

    public void redefine(String pattern, MatchField matchField, Category category, int priority,
                         boolean active) {

        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.matchField = Objects.requireNonNull(matchField, "matchField");
        this.category = Objects.requireNonNull(category, "category");
        this.priority = priority;
        this.active = active;
    }

    public Long getId() {

        return id;
    }

    public String getPattern() {

        return pattern;
    }

    public MatchField getMatchField() {

        return matchField;
    }

    public Category getCategory() {

        return category;
    }

    public int getPriority() {

        return priority;
    }

    public boolean isActive() {

        return active;
    }
}
