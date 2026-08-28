package com.rms.repository;

import com.rms.domain.Recipe;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    /**
     * The exact lookup the deduction engine performs: given a menu item that was just
     * ordered, what raw ingredients (and how much of each) does one unit consume.
     * EntityGraph pulls the Ingredient row in the same query since RecipeDeductionService
     * needs ingredient.id immediately afterward to acquire the pessimistic lock.
     */
    @EntityGraph(attributePaths = {"ingredient"})
    List<Recipe> findByMenuItemId(Long menuItemId);

    boolean existsByMenuItemIdAndIngredientId(Long menuItemId, Long ingredientId);
}
