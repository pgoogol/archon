package com.pgoogol.kitchen.recipe.application;

import com.pgoogol.kitchen.common.ErrorCodes;
import com.pgoogol.kitchen.common.ExceptionMessageConstants;
import com.pgoogol.kitchen.common.NotFoundException;
import com.pgoogol.kitchen.recipe.domain.Recipe;
import com.pgoogol.kitchen.recipe.domain.RecipeStatus;
import com.pgoogol.kitchen.recipe.infrastructure.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Odczyt przepisów i archiwizacja. Zapis idzie wyłącznie przez
 * {@link RecipeWriter} — dzięki temu nie ma drugiej drogi, która pomijałaby
 * dziennik zmian.
 */
@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipes;

    /**
     * Przepis z całą zawartością. Graf encji ładuje składniki i kroki jednym
     * zapytaniem — ekran przepisu i tak pokazuje wszystko naraz.
     */
    @Transactional(readOnly = true)
    public Recipe get(Long id) {

        Optional<Recipe> recipe = recipes.findWithContentById(id);
        return recipe.orElseThrow(() -> new NotFoundException(ErrorCodes.RECIPE_NOT_FOUND,
            ExceptionMessageConstants.RECIPE_NOT_FOUND.formatted(id)));
    }

    /**
     * Lista od najnowszego. Wyszukiwarka z filtrami dochodzi osobno — tutaj jest
     * to, co widać po wejściu do książki.
     */
    @Transactional(readOnly = true)
    public Page<Recipe> list(Pageable pageable) {

        return recipes.findByStatusOrderByCreatedAtDesc(RecipeStatus.ACTIVE, pageable);
    }

    /**
     * Przepis znika z książki, ale nie z bazy: dziennik zmian i zlecenia importu
     * trzymają do niego odniesienia, a skasowany wiersz zabrałby im sens.
     */
    @Transactional
    public void archive(Long id) {

        Recipe recipe = get(id);
        recipe.archive();
    }
}
