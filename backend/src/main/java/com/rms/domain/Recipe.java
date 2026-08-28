package com.rms.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Join entity mapping a MenuItem to one of its raw Ingredients plus the quantity
 * consumed per unit sold. This table is the entire lookup the Recipe Deduction
 * Engine walks when an order line is submitted.
 */
@Entity
@Table(
    name = "recipes",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_recipe_menu_item_ingredient",
        columnNames = {"menu_item_id", "ingredient_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_recipe_menu_item"))
    private MenuItem menuItem;

    /** ON DELETE RESTRICT at the DB level: an ingredient in active use cannot be dropped. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_recipe_ingredient"))
    private Ingredient ingredient;

    @Column(name = "quantity_required", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityRequired;
}
