package com.pgoogol.kitchen.ingredient.infrastructure;

import com.pgoogol.kitchen.ingredient.domain.Ingredient;
import com.pgoogol.kitchen.ingredient.domain.IngredientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    Optional<Ingredient> findByNameNormalized(String nameNormalized);

    Page<Ingredient> findByStatus(IngredientStatus status, Pageable pageable);

    Page<Ingredient> findByNameNormalizedContaining(String fragment, Pageable pageable);

    /**
     * Kandydaci na dopasowanie przy literówce („cukini" → „cukinia"). Podobieństwo
     * trigramów liczy Postgres — próg podaje wywołujący, żeby dało się go zmienić
     * bez ruszania zapytania.
     */
    @Query(value = """
        select i.* from kitchen.ingredient i \
        where similarity(i.name_normalized, :name) >= :threshold \
        order by similarity(i.name_normalized, :name) desc \
        limit :limit""", nativeQuery = true)
    List<Ingredient> findSimilar(String name, double threshold, int limit);
}
