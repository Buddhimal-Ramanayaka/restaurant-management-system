package com.rms.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "menu_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private Boolean isAvailable = true;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    /**
     * Recipe is the join entity between MenuItem and Ingredient (Module 2.2).
     * mappedBy on the Recipe.menuItem side; cascade so editing a menu item recipe
     * from the admin screen persists new rows in one call, orphanRemoval so
     * deleting a line from the recipe editor actually deletes the mapping row.
     */
    @OneToMany(mappedBy = "menuItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Recipe> recipes = new HashSet<>();

    /** Optimistic lock for the menu item row itself (price/availability edits from Admin UI). */
    @Version
    private Long version;
}
