package com.pgoogol.kitchen.revision.domain;

import com.pgoogol.kitchen.recipe.domain.Recipe;
import com.pgoogol.kitchen.revision.diff.RevisionOrigin;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Jedna zapisana zmiana przepisu. Rewizja 1 powstaje przy założeniu przepisu,
 * każda kolejna przy edycji — i niesie wyłącznie pola, które faktycznie się
 * zmieniły.
 *
 * <p>Historia rośnie i nigdy nie jest przepisywana: przywrócenie starszej
 * wersji dopisuje kolejną rewizję z {@link RevisionOrigin#RESTORE}, zamiast
 * kasować to, co było pomiędzy.</p>
 */
@Entity
@Table(name = "recipe_revision")
public class RecipeRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RevisionOrigin origin;

    @Column(name = "change_summary", length = 500)
    private String changeSummary;

    @Column(name = "restored_from_revision_no")
    private Integer restoredFromRevisionNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "revision", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeChange> changes = new ArrayList<>();

    protected RecipeRevision() {

    }

    public RecipeRevision(Recipe recipe, int revisionNo, RevisionOrigin origin, String changeSummary) {

        this.recipe = Objects.requireNonNull(recipe, "recipe");
        this.revisionNo = revisionNo;
        this.origin = Objects.requireNonNull(origin, "origin");
        this.changeSummary = changeSummary;
        this.createdAt = Instant.now();
    }

    public RecipeChange record(RecipeChange change) {

        changes.add(change);
        return change;
    }

    public void setRestoredFromRevisionNo(Integer restoredFromRevisionNo) {

        this.restoredFromRevisionNo = restoredFromRevisionNo;
    }

    public Long getId() {

        return id;
    }

    public Recipe getRecipe() {

        return recipe;
    }

    public int getRevisionNo() {

        return revisionNo;
    }

    public RevisionOrigin getOrigin() {

        return origin;
    }

    public String getChangeSummary() {

        return changeSummary;
    }

    public Integer getRestoredFromRevisionNo() {

        return restoredFromRevisionNo;
    }

    public Instant getCreatedAt() {

        return createdAt;
    }

    public List<RecipeChange> getChanges() {

        return changes;
    }
}
