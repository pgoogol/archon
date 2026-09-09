package com.pgoogol.kitchen.recipe.infrastructure;

import com.pgoogol.kitchen.recipe.domain.RecipeNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeNoteRepository extends JpaRepository<RecipeNote, Long> {

    List<RecipeNote> findByRecipeIdOrderByCreatedAtDesc(Long recipeId);
}
