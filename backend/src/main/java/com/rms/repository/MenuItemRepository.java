package com.rms.repository;

import com.rms.domain.MenuItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByCategory(String category);

    List<MenuItem> findByIsAvailableTrue();

    /**
     * The POS grid loads every available item plus its recipe lines up front, so the
     * per-item add-to-cart action never needs an extra round trip. EntityGraph avoids
     * N+1 without turning on open-in-view.
     */
    @EntityGraph(attributePaths = {"recipes", "recipes.ingredient"})
    List<MenuItem> findAll();
}
