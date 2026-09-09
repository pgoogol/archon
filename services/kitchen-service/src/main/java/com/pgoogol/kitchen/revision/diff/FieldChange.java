package com.pgoogol.kitchen.revision.diff;

import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.Line;

import java.util.Objects;

/**
 * Jedna zmiana w dzienniku: co, które pole, z czego i na co.
 *
 * @param targetLabel czytelna etykieta („cebula", „krok 3") — historia ma się
 *                    dać przeczytać bez dociągania wierszy, których już nie ma
 * @param removedRow  pełna treść usuniętego wiersza; wypełniona wyłącznie dla
 *                    {@link ChangeOperation#REMOVE}, bo tylko ona jest nie do
 *                    odtworzenia z samego identyfikatora
 */
public record FieldChange(
    TargetType targetType,
    Long targetId,
    String targetLabel,
    ChangeOperation operation,
    String field,
    String oldValue,
    String newValue,
    Line removedRow) {

    public FieldChange {

        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(operation, "operation");
    }

    public static FieldChange updated(TargetType type, Long id, String label,
                                      String field, String oldValue, String newValue) {

        return new FieldChange(type, id, label, ChangeOperation.UPDATE, field, oldValue, newValue, null);
    }

    public static FieldChange moved(TargetType type, Long id, String label,
                                    int oldPosition, int newPosition) {

        String from = String.valueOf(oldPosition);
        String to = String.valueOf(newPosition);
        return new FieldChange(type, id, label, ChangeOperation.MOVE, "position", from, to, null);
    }

    public static FieldChange added(TargetType type, Long id, String label) {

        return new FieldChange(type, id, label, ChangeOperation.ADD, null, null, null, null);
    }

    public static FieldChange removed(TargetType type, Long id, String label, Line row) {

        return new FieldChange(type, id, label, ChangeOperation.REMOVE, null, null, null, row);
    }
}
