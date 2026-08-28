package com.rms.integration;

import com.rms.domain.*;
import com.rms.domain.enums.LedgerReason;
import com.rms.domain.enums.Role;
import com.rms.domain.enums.TableStatus;
import com.rms.domain.enums.UnitType;
import com.rms.dto.request.CreateOrderRequest;
import com.rms.dto.request.OrderItemRequest;
import com.rms.dto.request.UpdateOrderStatusRequest;
import com.rms.dto.response.AuthResponse;
import com.rms.dto.response.OrderResponse;
import com.rms.dto.response.TableResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-01: End-to-end order lifecycle. Exercises the real REST layer, real Spring
 * Security filter chain, and a real MySQL container - the only mocked component
 * is nothing; this is deliberately a black-box test hitting the same endpoints
 * a browser would.
 */
class OrderLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private IngredientRepository ingredientRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private RecipeRepository recipeRepository;
    @Autowired private RestaurantTableRepository tableRepository;
    @Autowired private InventoryLedgerRepository ledgerRepository;

    private Long tableId;
    private Long menuItemId;
    private String jwt;

    @BeforeEach
    void seedData() {
        User waiter = userRepository.save(User.builder()
                .username("it01-waiter").password(passwordEncoder.encode("password123"))
                .role(Role.WAITER).isActive(true).fullName("Test Waiter").build());

        Ingredient rice = ingredientRepository.save(Ingredient.builder()
                .name("IT01-Rice").currentStock(new BigDecimal("1000"))
                .reorderLevel(new BigDecimal("100")).unitType(UnitType.KG)
                .averageUnitCost(BigDecimal.TEN).build());

        MenuItem biryani = menuItemRepository.save(MenuItem.builder()
                .name("IT01-Biryani").price(new BigDecimal("450")).category("Main Course")
                .isAvailable(true).build());
        menuItemId = biryani.getId();

        recipeRepository.save(Recipe.builder().menuItem(biryani).ingredient(rice)
                .quantityRequired(new BigDecimal("250")).build());

        RestaurantTable table = tableRepository.save(RestaurantTable.builder()
                .tableNumber("IT01-T1").seatingCapacity(4).operationalStatus(TableStatus.AVAILABLE).build());
        tableId = table.getId();

        AuthResponse auth = restTemplate.postForObject(
                "/api/auth/login",
                new com.rms.dto.request.LoginRequest("it01-waiter", "password123"),
                AuthResponse.class);
        jwt = auth.token();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return headers;
    }

    @Test
    @DisplayName("IT-01: full order lifecycle PENDING -> PREPARING -> READY -> BILLED -> COMPLETED")
    void fullOrderLifecycle_succeeds() {
        // 1. Submit the order
        CreateOrderRequest createRequest = new CreateOrderRequest(
                tableId, java.util.List.of(new OrderItemRequest(menuItemId, 1, null)), null);

        var createResponse = restTemplate.exchange(
                "/api/orders", HttpMethod.POST,
                new HttpEntity<>(createRequest, authHeaders()), OrderResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long orderId = createResponse.getBody().id();
        assertThat(createResponse.getBody().status()).isEqualTo(com.rms.domain.enums.OrderStatus.PENDING);

        // Recipe deduction happened as part of order submission - verify the ledger.
        var ledgerEntries = ledgerRepository.findByIngredientIdOrderByRecordedAtDesc(
                recipeRepository.findByMenuItemId(menuItemId).get(0).getIngredient().getId());
        assertThat(ledgerEntries).isNotEmpty();
        assertThat(ledgerEntries.get(0).getReason()).isEqualTo(LedgerReason.RECIPE_DEDUCTION);

        // 2. PENDING -> PREPARING
        var preparingResponse = patchStatus(orderId, com.rms.domain.enums.OrderStatus.PREPARING);
        assertThat(preparingResponse.getBody().status()).isEqualTo(com.rms.domain.enums.OrderStatus.PREPARING);

        // 3. PREPARING -> READY
        var readyResponse = patchStatus(orderId, com.rms.domain.enums.OrderStatus.READY);
        assertThat(readyResponse.getBody().status()).isEqualTo(com.rms.domain.enums.OrderStatus.READY);

        // 4. READY -> BILLED (table should follow to BILLED)
        var billedResponse = patchStatus(orderId, com.rms.domain.enums.OrderStatus.BILLED);
        assertThat(billedResponse.getBody().status()).isEqualTo(com.rms.domain.enums.OrderStatus.BILLED);

        var tableAfterBilled = restTemplate.exchange(
                "/api/tables", HttpMethod.GET, new HttpEntity<>(authHeaders()), TableResponse[].class).getBody();
        TableResponse thisTable = java.util.Arrays.stream(tableAfterBilled)
                .filter(t -> t.id().equals(tableId)).findFirst().orElseThrow();
        assertThat(thisTable.operationalStatus()).isEqualTo(TableStatus.BILLED);

        // 5. BILLED -> COMPLETED (table should follow to CLEANING, not AVAILABLE)
        var completedResponse = patchStatus(orderId, com.rms.domain.enums.OrderStatus.COMPLETED);
        assertThat(completedResponse.getBody().status()).isEqualTo(com.rms.domain.enums.OrderStatus.COMPLETED);

        RestaurantTable finalTableState = tableRepository.findById(tableId).orElseThrow();
        assertThat(finalTableState.getOperationalStatus()).isEqualTo(TableStatus.CLEANING);
    }

    private org.springframework.http.ResponseEntity<OrderResponse> patchStatus(Long orderId, com.rms.domain.enums.OrderStatus status) {
        return restTemplate.exchange(
                "/api/orders/" + orderId + "/status", HttpMethod.PATCH,
                new HttpEntity<>(new UpdateOrderStatusRequest(status, null), authHeaders()),
                OrderResponse.class);
    }
}
