package com.pgoogol.kitchen.ingredient.infrastructure;

import com.pgoogol.kitchen.ingredient.domain.IngredientAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IngredientAliasRepository extends JpaRepository<IngredientAlias, Long> {

    Optional<IngredientAlias> findByAliasNormalized(String aliasNormalized);

    List<IngredientAlias> findByIngredientIdOrderByAliasAsc(Long ingredientId);
}
