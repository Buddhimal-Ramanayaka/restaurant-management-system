package com.rms.service;

import com.rms.aspect.AuditableAction;
import com.rms.domain.Ingredient;
import com.rms.domain.MenuItem;
import com.rms.domain.Recipe;
import com.rms.dto.request.MenuItemRequest;
import com.rms.dto.request.RecipeLineRequest;
import com.rms.dto.response.MenuItemDetailResponse;
import com.rms.exception.ResourceNotFoundException;
import com.rms.repository.IngredientRepository;
import com.rms.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Module 2.1 (Admin: global menu config) + Module 2.2 (recipe mapping). */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuItemRepository menuItemRepository;
    private final IngredientRepository ingredientRepository;

    @Transactional(readOnly = true)
    public List<MenuItem> findAll() {
        return menuItemRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<MenuItem> findAvailable() {
        return menuItemRepository.findByIsAvailableTrue();
    }

    /** DTO mapping happens inside this @Transactional method - MenuItem.recipes and each
     *  Recipe.ingredient are lazy, and this project runs with open-in-view: false. */
    @Transactional(readOnly = true)
    public MenuItemDetailResponse findByIdWithRecipes(Long id) {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found: " + id));
        return MenuItemDetailResponse.from(menuItem);
    }

    /**
     * Creates or fully replaces a menu item recipe mapping in one call, so the Admin
     * "recipe mapping" screen (Module 2.1) can save the whole card - name, price,
     * category, and every ingredient line - as a single atomic request.
     */
    @Transactional
    @AuditableAction("MENU_ITEM_RECIPE_UPDATE")
    public MenuItem createOrUpdate(Long existingId, MenuItemRequest request) {
        MenuItem menuItem = existingId != null
                ? menuItemRepository.findById(existingId)
                        .orElseThrow(() -> new ResourceNotFoundException("Menu item not found: " + existingId))
                : MenuItem.builder().isAvailable(true).build();

        menuItem.setName(request.name());
        menuItem.setPrice(request.price());
        menuItem.setCategory(request.category());
        menuItem.setImageUrl(request.imageUrl());

        if (request.recipeLines() != null) {
            menuItem.getRecipes().clear(); // orphanRemoval on MenuItem.recipes deletes the old rows
            for (RecipeLineRequest line : request.recipeLines()) {
                Ingredient ingredient = ingredientRepository.findById(line.ingredientId())
                        .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found: " + line.ingredientId()));
                menuItem.getRecipes().add(Recipe.builder()
                        .menuItem(menuItem)
                        .ingredient(ingredient)
                        .quantityRequired(line.quantityRequired())
                        .build());
            }
        }

        return menuItemRepository.save(menuItem);
    }

    @Transactional
    public MenuItem setAvailability(Long menuItemId, boolean available) {
        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found: " + menuItemId));
        menuItem.setIsAvailable(available);
        return menuItemRepository.save(menuItem);
    }
}
