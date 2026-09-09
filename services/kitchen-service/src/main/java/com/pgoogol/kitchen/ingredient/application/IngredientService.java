package com.pgoogol.kitchen.ingredient.application;

import com.pgoogol.kitchen.common.ConflictException;
import com.pgoogol.kitchen.common.ErrorCodes;
import com.pgoogol.kitchen.common.ExceptionMessageConstants;
import com.pgoogol.kitchen.common.NotFoundException;
import com.pgoogol.kitchen.dictionary.application.TermService;
import com.pgoogol.kitchen.dictionary.domain.NameNormalizer;
import com.pgoogol.kitchen.dictionary.domain.Unit;
import com.pgoogol.kitchen.ingredient.domain.Ingredient;
import com.pgoogol.kitchen.ingredient.domain.IngredientAlias;
import com.pgoogol.kitchen.ingredient.domain.IngredientStatus;
import com.pgoogol.kitchen.ingredient.infrastructure.IngredientAliasRepository;
import com.pgoogol.kitchen.ingredient.infrastructure.IngredientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Katalog składników: przeglądanie, dopisywanie i synonimy.
 *
 * <p>Katalog rośnie z tego, co realnie gotujesz — import i formularz dopisują
 * pozycje ze statusem {@link IngredientStatus#NEW}, a ekran katalogu pozwala je
 * przejrzeć, poprawić nazwę i podpiąć synonimy.</p>
 */
@Service
@RequiredArgsConstructor
public class IngredientService {

    private final IngredientRepository ingredients;
    private final IngredientAliasRepository aliases;
    private final TermService terms;

    @Transactional(readOnly = true)
    public Page<Ingredient> search(String query, IngredientStatus status, Pageable pageable) {

        String normalized = NameNormalizer.normalize(query);
        if (Objects.nonNull(normalized) && !normalized.isBlank()) {

            return ingredients.findByNameNormalizedContaining(normalized, pageable);
        }
        if (Objects.nonNull(status)) {

            return ingredients.findByStatus(status, pageable);
        }
        return ingredients.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Ingredient get(Long id) {

        Optional<Ingredient> ingredient = ingredients.findById(id);
        return ingredient.orElseThrow(() -> new NotFoundException(ErrorCodes.INGREDIENT_NOT_FOUND,
            ExceptionMessageConstants.INGREDIENT_NOT_FOUND.formatted(id)));
    }

    @Transactional(readOnly = true)
    public List<IngredientAlias> aliasesOf(Long ingredientId) {

        return aliases.findByIngredientIdOrderByAliasAsc(ingredientId);
    }

    @Transactional
    public Ingredient create(String name, String category, String defaultUnitCode) {

        String normalized = NameNormalizer.normalize(name);
        Optional<Ingredient> existing = ingredients.findByNameNormalized(normalized);
        if (existing.isPresent()) {

            throw new ConflictException(ErrorCodes.INGREDIENT_EXISTS,
                ExceptionMessageConstants.INGREDIENT_EXISTS.formatted(name));
        }
        Ingredient ingredient = new Ingredient(name, IngredientStatus.VERIFIED);
        ingredient.setCategory(category);
        ingredient.setDefaultUnit(unit(defaultUnitCode));
        return ingredients.save(ingredient);
    }

    @Transactional
    public Ingredient update(Long id, String name, String category, String defaultUnitCode,
                             IngredientStatus status) {

        Ingredient ingredient = get(id);
        if (Objects.nonNull(name)) {

            ingredient.rename(name);
        }
        ingredient.setCategory(category);
        ingredient.setDefaultUnit(unit(defaultUnitCode));
        if (Objects.nonNull(status)) {

            ingredient.setStatus(status);
        }
        return ingredient;
    }

    @Transactional
    public IngredientAlias addAlias(Long ingredientId, String alias, String language) {

        Ingredient ingredient = get(ingredientId);
        String normalized = NameNormalizer.normalize(alias);
        Optional<IngredientAlias> existing = aliases.findByAliasNormalized(normalized);
        if (existing.isPresent()) {

            throw new ConflictException(ErrorCodes.ALIAS_EXISTS,
                ExceptionMessageConstants.ALIAS_EXISTS.formatted(alias));
        }
        IngredientAlias created = new IngredientAlias(ingredient, alias, language);
        return aliases.save(created);
    }

    @Transactional
    public void deleteAlias(Long ingredientId, Long aliasId) {

        Optional<IngredientAlias> found = aliases.findById(aliasId);
        IngredientAlias alias = found.orElseThrow(() -> new NotFoundException(
            ErrorCodes.ALIAS_NOT_FOUND, ExceptionMessageConstants.ALIAS_NOT_FOUND.formatted(aliasId)));
        if (!Objects.equals(alias.getIngredient().getId(), ingredientId)) {

            throw new NotFoundException(ErrorCodes.ALIAS_NOT_FOUND,
                ExceptionMessageConstants.ALIAS_NOT_FOUND.formatted(aliasId));
        }
        aliases.delete(alias);
    }

    private Unit unit(String code) {

        Optional<Unit> resolved = terms.findUnit(code);
        return resolved.orElse(null);
    }
}
