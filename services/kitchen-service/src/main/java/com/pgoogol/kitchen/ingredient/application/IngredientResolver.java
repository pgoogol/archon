package com.pgoogol.kitchen.ingredient.application;

import com.pgoogol.kitchen.dictionary.domain.NameNormalizer;
import com.pgoogol.kitchen.ingredient.domain.Ingredient;
import com.pgoogol.kitchen.ingredient.domain.IngredientAlias;
import com.pgoogol.kitchen.ingredient.domain.IngredientStatus;
import com.pgoogol.kitchen.ingredient.infrastructure.IngredientAliasRepository;
import com.pgoogol.kitchen.ingredient.infrastructure.IngredientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Dopasowanie nazwy z przepisu do pozycji katalogu.
 *
 * <p>Kolejność jest celowa: najpierw alias, potem nazwa kanoniczna, na końcu
 * podobieństwo trigramów („cukini" → „cukinia"). Dopiero gdy nic nie pasuje,
 * powstaje nowa pozycja ze statusem {@link IngredientStatus#NEW} — do
 * przejrzenia na ekranie katalogu.</p>
 */
@Service
@RequiredArgsConstructor
public class IngredientResolver {

    /** Poniżej tego progu podobieństwo bardziej myli, niż pomaga. */
    private static final double SIMILARITY_THRESHOLD = 0.85;

    private static final int SIMILARITY_LIMIT = 1;

    private final IngredientRepository ingredients;
    private final IngredientAliasRepository aliases;

    @Transactional(readOnly = true)
    public Optional<Ingredient> resolve(String name) {

        String normalized = NameNormalizer.normalize(name);
        if (Objects.isNull(normalized) || normalized.isBlank()) {

            return Optional.empty();
        }
        Optional<IngredientAlias> alias = aliases.findByAliasNormalized(normalized);
        if (alias.isPresent()) {

            return Optional.of(target(alias.get().getIngredient()));
        }
        Optional<Ingredient> exact = ingredients.findByNameNormalized(normalized);
        if (exact.isPresent()) {

            return Optional.of(target(exact.get()));
        }
        return similar(normalized);
    }

    /**
     * Dopasowuje, a gdy nie ma czego — zakłada pozycję. Używane przy zapisie
     * przepisu: katalog rośnie z tego, co realnie gotujesz, a nie z listy
     * wszystkich produktów świata.
     */
    @Transactional
    public Optional<Ingredient> resolveOrCreate(String name) {

        Optional<Ingredient> resolved = resolve(name);
        if (resolved.isPresent()) {

            return resolved;
        }
        String trimmed = trimmed(name);
        if (Objects.isNull(trimmed)) {

            return Optional.empty();
        }
        Ingredient created = new Ingredient(trimmed, IngredientStatus.NEW);
        return Optional.of(ingredients.save(created));
    }

    private Optional<Ingredient> similar(String normalized) {

        List<Ingredient> candidates =
            ingredients.findSimilar(normalized, SIMILARITY_THRESHOLD, SIMILARITY_LIMIT);
        if (candidates.isEmpty()) {

            return Optional.empty();
        }
        return Optional.of(target(candidates.getFirst()));
    }

    /** Scalony duplikat prowadzi do pozycji, w którą go wtopiono. */
    private Ingredient target(Ingredient ingredient) {

        Ingredient merged = ingredient.getMergedInto();
        if (Objects.isNull(merged)) {

            return ingredient;
        }
        return merged;
    }

    private String trimmed(String name) {

        if (Objects.isNull(name)) {

            return null;
        }
        String value = name.trim();
        if (value.isBlank()) {

            return null;
        }
        return value;
    }
}
