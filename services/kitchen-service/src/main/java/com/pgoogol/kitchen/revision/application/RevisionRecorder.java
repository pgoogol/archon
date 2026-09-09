package com.pgoogol.kitchen.revision.application;

import com.pgoogol.kitchen.recipe.domain.Recipe;
import com.pgoogol.kitchen.revision.diff.ChangeOperation;
import com.pgoogol.kitchen.revision.diff.FieldChange;
import com.pgoogol.kitchen.revision.diff.RecipeSnapshot.Line;
import com.pgoogol.kitchen.revision.diff.RevisionOrigin;
import com.pgoogol.kitchen.revision.diff.TargetType;
import com.pgoogol.kitchen.revision.domain.RecipeChange;
import com.pgoogol.kitchen.revision.domain.RecipeRevision;
import com.pgoogol.kitchen.revision.infrastructure.RecipeRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;

/**
 * Zapis rewizji razem ze zmienionymi polami.
 *
 * <p>Usunięty wiersz wędruje do dziennika w całości — bez tego nie dałoby się go
 * odtworzyć przy cofaniu zmian. Serializacja jest tutaj, a nie w rachunku różnic,
 * żeby tamten pakiet został wolny od Jacksona i dał się testować bez niczego.</p>
 */
@Service
@RequiredArgsConstructor
public class RevisionRecorder {

    private final RecipeRevisionRepository revisions;
    private final ObjectMapper objectMapper;

    @Transactional
    public RecipeRevision record(Recipe recipe, List<FieldChange> changes,
                                 RevisionOrigin origin, String changeSummary) {

        int revisionNo = recipe.nextRevisionNo();
        RecipeRevision revision = new RecipeRevision(recipe, revisionNo, origin, changeSummary);
        changes.forEach(change -> revision.record(toEntity(revision, change)));
        return revisions.save(revision);
    }

    /** Rewizja pierwsza: jeden wpis „utworzono", zamiast listy pól z pustki. */
    @Transactional
    public RecipeRevision recordCreation(Recipe recipe, RevisionOrigin origin, String changeSummary) {

        FieldChange creation = FieldChange.added(TargetType.RECIPE, null, recipe.getTitle());
        return record(recipe, List.of(creation), origin, changeSummary);
    }

    private RecipeChange toEntity(RecipeRevision revision, FieldChange change) {

        return new RecipeChange(
            revision,
            change.targetType(),
            change.targetId(),
            change.targetLabel(),
            change.operation(),
            change.field(),
            change.oldValue(),
            change.newValue(),
            removedRow(change));
    }

    private String removedRow(FieldChange change) {

        Line row = change.removedRow();
        if (Objects.isNull(row) || change.operation() != ChangeOperation.REMOVE) {

            return null;
        }
        return objectMapper.writeValueAsString(row);
    }
}
