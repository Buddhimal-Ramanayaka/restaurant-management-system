package com.rms.dto.response;

import com.rms.domain.MenuItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/** Admin "Configure Menu Items" / "Define Recipe Mappings" editor (Figure 2.1) - the plain
 *  MenuItemResponse the POS grid uses doesn't carry recipe lines, since that would be dead
 *  weight on every POS terminal's menu fetch just to support the one Admin edit screen. */
public record MenuItemDetailResponse(
        Long id,
        String name,
        BigDecimal price,
        String category,
        String imageUrl,
        Boolean isAvailable,
        List<RecipeLineResponse> recipeLines
) {
    public static MenuItemDetailResponse from(MenuItem item) {
        return new MenuItemDetailResponse(
                item.getId(), item.getName(), item.getPrice(), item.getCategory(),
                item.getImageUrl(), item.getIsAvailable(),
                item.getRecipes().stream().map(RecipeLineResponse::from).collect(Collectors.toList())
        );
    }
}
