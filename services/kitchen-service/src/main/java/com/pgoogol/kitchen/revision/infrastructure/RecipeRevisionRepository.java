package com.pgoogol.kitchen.revision.infrastructure;

import com.pgoogol.kitchen.revision.domain.RecipeRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeRevisionRepository extends JpaRepository<RecipeRevision, Long> {

    List<RecipeRevision> findByRecipeIdOrderByRevisionNoDesc(Long recipeId);
}
