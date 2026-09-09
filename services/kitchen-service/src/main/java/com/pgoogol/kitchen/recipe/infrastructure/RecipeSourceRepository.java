package com.pgoogol.kitchen.recipe.infrastructure;

import com.pgoogol.kitchen.recipe.domain.RecipeSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecipeSourceRepository extends JpaRepository<RecipeSource, Long> {

    Optional<RecipeSource> findByUrlNormalized(String urlNormalized);
}
