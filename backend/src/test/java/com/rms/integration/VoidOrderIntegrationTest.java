package com.rms.integration;

import com.rms.domain.*;
import com.rms.domain.enums.OrderStatus;
import com.rms.domain.enums.Role;
import com.rms.domain.enums.TableStatus;
import com.rms.domain.enums.UnitType;
import com.rms.dto.request.CreateOrderRequest;
import com.rms.dto.request.OrderItemRequest;
import com.rms.dto.response.AuthResponse;
import com.rms.dto.response.OrderResponse;
import com.rms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-07: voiding an order releases the table but does NOT reverse the stock
 * deduction that already occurred at submission time. This is a deliberate
 * design choice, not an oversight - see OrderService.voidOrder: a void
 * represents "the kitchen never made this" only from a service standpoint,
 * but the ingredients were physically pulled from the pantry the moment the
 * order was submitted, and only an explicit manual stock correction (Module
 * 2.9, IngredientService.correctStock) should restore inventory that was
 * already staged for preparation.
 */
class VoidOrderIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private IngredientRepository ingredientRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private RecipeRepository recipeRepository;
    @Autowired private RestaurantTableRepository tableRepository;

    private Long ingredientId;
    private Long tableId;
    private Long menuItemId;
    private String jwt;

    @BeforeEach
    void seedData() {
        userRepository.save(User.builder()
                .username("it07-waiter").password(passwordEncoder.encode("password123"))
                .role(Role.WAITER).isActive(true).fullName("Test Waiter").build());

        Ingredient ingredient = ingredientRepository.save(Ingredient.builder()
                .name("IT07-Ingredient").currentStock(new BigDecimal("500"))
                .reorderLevel(new BigDecimal("50")).unitType(UnitType.KG)
                .averageUnitCost(BigDecimal.TEN).build());
        ingredientId = ingredient.getId();

        MenuItem dish = menuItemRepository.save(MenuItem.builder()
                .name("IT07-Dish").price(BigDecimal.valueOf(300)).category("Main Course").isAvailable(true).build());
        menuItemId = dish.getId();

        recipeRepository.save(Recipe.builder().menuItem(dish).ingredient(ingredient)
                .quantityRequired(new BigDecimal("100")).build());

        RestaurantTable table = tableRepository.save(RestaurantTable.builder()
                .tableNumber("IT07-T1").seatingCapacity(2).operationalStatus(TableStatus.AVAILABLE).build());
        tableId = table.getId();

        AuthResponse auth = restTemplate.postForObject(
                "/api/auth/login",
                new com.rms.dto.request.LoginRequest("it07-waiter", "password123"),
                AuthResponse.class);
        jwt = auth.token();
    }

    @Test
    @DisplayName("IT-07: Voiding an order releases the table but stock stays deducted")
    void voidOrder_stockNotRefunded() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);

        CreateOrderRequest createRequest = new CreateOrderRequest(
                tableId, List.of(new OrderItemRequest(menuItemId, 1, null)), null);
        var createResponse = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(createRequest, headers), OrderResponse.class);
        Long orderId = createResponse.getBody().id();

        Ingredient afterSubmit = ingredientRepository.findById(ingredientId).orElseThrow();
        assertThat(afterSubmit.getCurrentStock()).isEqualByComparingTo("400"); // 500 - 100

        var voidResponse = restTemplate.exchange(
                "/api/orders/" + orderId + "/void", HttpMethod.POST, new HttpEntity<>(headers), OrderResponse.class);

        assertThat(voidResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(voidResponse.getBody().status()).isEqualTo(OrderStatus.VOID);

        // The critical assertion: stock remains at 400, NOT restored to 500.
        Ingredient afterVoid = ingredientRepository.findById(ingredientId).orElseThrow();
        assertThat(afterVoid.getCurrentStock()).isEqualByComparingTo("400");

        RestaurantTable tableAfterVoid = tableRepository.findById(tableId).orElseThrow();
        assertThat(tableAfterVoid.getOperationalStatus()).isEqualTo(TableStatus.AVAILABLE);
        assertThat(tableAfterVoid.getCurrentOrderId()).isNull();
    }
}
