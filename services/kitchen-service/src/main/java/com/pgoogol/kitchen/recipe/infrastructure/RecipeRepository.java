package com.pgoogol.kitchen.recipe.infrastructure;

import com.pgoogol.kitchen.recipe.domain.Recipe;
import com.pgoogol.kitchen.recipe.domain.RecipeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    /**
     * Odczyt przepisu z całą zawartością. Bez grafu każdy krok i każdy składnik
     * kosztowałby osobne zapytanie — a ekran przepisu pokazuje je wszystkie naraz.
     */
    @EntityGraph(attributePaths = {"ingredients", "ingredients.alternatives", "steps", "tags", "diets"})
    Optional<Recipe> findWithContentById(Long id);

    Page<Recipe> findByStatusOrderByCreatedAtDesc(RecipeStatus status, Pageable pageable);
}
