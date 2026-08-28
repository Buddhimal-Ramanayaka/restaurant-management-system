package com.rms.dto.response;

import com.rms.domain.MenuItem;

import java.math.BigDecimal;

public record MenuItemResponse(
        Long id,
        String name,
        BigDecimal price,
        String category,
        Boolean isAvailable,
        String imageUrl
) {
    public static MenuItemResponse from(MenuItem item) {
        return new MenuItemResponse(
                item.getId(), item.getName(), item.getPrice(),
                item.getCategory(), item.getIsAvailable(), item.getImageUrl()
        );
    }
}
