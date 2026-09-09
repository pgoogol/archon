package com.pgoogol.kitchen.revision.domain;

import com.pgoogol.kitchen.revision.diff.ChangeOperation;
import com.pgoogol.kitchen.revision.diff.TargetType;
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
 * Pojedyncze zmienione pole. Wierszy jest tyle, ile pól naprawdę się zmieniło —
 * podniesienie ilości cebuli z jednej sztuki na dwie to jeden wiersz, a nie
 * kopia całego przepisu.
 *
 * <p>{@code removedRow} niesie pełną treść usuniętego wiersza, bo bez niej nie
 * dałoby się go odtworzyć przy cofaniu zmian. Trzymamy go jako tekst: nigdy nie
 * zaglądamy do środka zapytaniem, to ładunek do odczytania w całości.</p>
 */
@Entity
@Table(name = "recipe_change")
public class RecipeChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private RecipeRevision revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private TargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_label", length = 300)
    private String targetLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChangeOperation operation;

    @Column(length = 50)
    private String field;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "removed_row")
    private String removedRow;

    protected RecipeChange() {

    }

    public RecipeChange(RecipeRevision revision, TargetType targetType, Long targetId,
                        String targetLabel, ChangeOperation operation, String field,
                        String oldValue, String newValue, String removedRow) {

        this.revision = Objects.requireNonNull(revision, "revision");
        this.targetType = Objects.requireNonNull(targetType, "targetType");
        this.targetId = targetId;
        this.targetLabel = targetLabel;
        this.operation = Objects.requireNonNull(operation, "operation");
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.removedRow = removedRow;
    }

    public Long getId() {

        return id;
    }

    public RecipeRevision getRevision() {

        return revision;
    }

    public TargetType getTargetType() {

        return targetType;
    }

    public Long getTargetId() {

        return targetId;
    }

    public String getTargetLabel() {

        return targetLabel;
    }

    public ChangeOperation getOperation() {

        return operation;
    }

    public String getField() {

        return field;
    }

    public String getOldValue() {

        return oldValue;
    }

    public String getNewValue() {

        return newValue;
    }

    public String getRemovedRow() {

        return removedRow;
    }
}
