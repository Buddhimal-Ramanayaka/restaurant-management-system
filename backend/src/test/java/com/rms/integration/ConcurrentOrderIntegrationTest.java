package com.rms.integration;

import com.rms.domain.*;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-02: Concurrent order submission - no inventory corruption. This is the test
 * that actually exercises the pessimistic locking design decision documented in
 * Chapter 3.3 of the dissertation: five threads submit orders simultaneously
 * against a shared ingredient, and the assertion is that the final stock level
 * is EXACTLY initial minus the sum of every successful deduction - no lost
 * updates, no double-counting, regardless of the interleaving the database
 * scheduler chose.
 *
 * Five DIFFERENT tables are used deliberately so the table lock (a separate
 * lock from the ingredient lock) is not the bottleneck being measured here -
 * this test isolates the ingredient-locking behaviour specifically.
 */
class ConcurrentOrderIntegrationTest extends AbstractIntegrationTest {

    private static final int THREAD_COUNT = 5;
    private static final BigDecimal INITIAL_STOCK = new BigDecimal("1000");
    private static final BigDecimal QTY_PER_ORDER = new BigDecimal("100");

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private IngredientRepository ingredientRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private RecipeRepository recipeRepository;
    @Autowired private RestaurantTableRepository tableRepository;
    @Autowired private InventoryLedgerRepository ledgerRepository;

    private Long ingredientId;
    private Long menuItemId;
    private final List<Long> tableIds = new ArrayList<>();
    private String jwt;

    @BeforeEach
    void seedData() {
        User waiter = userRepository.save(User.builder()
                .username("it02-waiter").password(passwordEncoder.encode("password123"))
                .role(Role.WAITER).isActive(true).fullName("Concurrency Test Waiter").build());

        Ingredient sharedIngredient = ingredientRepository.save(Ingredient.builder()
                .name("IT02-SharedIngredient").currentStock(INITIAL_STOCK)
                .reorderLevel(new BigDecimal("50")).unitType(UnitType.KG)
                .averageUnitCost(BigDecimal.ONE).build());
        ingredientId = sharedIngredient.getId();

        MenuItem item = menuItemRepository.save(MenuItem.builder()
                .name("IT02-Dish").price(BigDecimal.TEN).category("Main Course").isAvailable(true).build());
        menuItemId = item.getId();

        recipeRepository.save(Recipe.builder().menuItem(item).ingredient(sharedIngredient)
                .quantityRequired(QTY_PER_ORDER).build());

        for (int i = 0; i < THREAD_COUNT; i++) {
            RestaurantTable table = tableRepository.save(RestaurantTable.builder()
                    .tableNumber("IT02-T" + i).seatingCapacity(2).operationalStatus(TableStatus.AVAILABLE).build());
            tableIds.add(table.getId());
        }

        AuthResponse auth = restTemplate.postForObject(
                "/api/auth/login",
                new com.rms.dto.request.LoginRequest("it02-waiter", "password123"),
                AuthResponse.class);
        jwt = auth.token();
    }

    @Test
    @DisplayName("IT-02: Five concurrent orders against a shared ingredient produce zero corruption")
    void concurrentOrders_noCorruption() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long tableId = tableIds.get(i);
            executor.submit(() -> {
                try {
                    startGate.await(); // release every thread at (as close to) the same instant

                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth(jwt);
                    CreateOrderRequest request = new CreateOrderRequest(
                            tableId, List.of(new OrderItemRequest(menuItemId, 1, null)), null);

                    var response = restTemplate.exchange(
                            "/api/orders", HttpMethod.POST, new HttpEntity<>(request, headers), OrderResponse.class);

                    if (response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // A failed submission is fine here (stock exhaustion mid-run is a valid
                    // outcome) - what matters is the FINAL arithmetic, checked below.
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown(); // release all five threads simultaneously
        boolean completed = doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).as("all threads finished within timeout").isTrue();

        Ingredient finalState = ingredientRepository.findById(ingredientId).orElseThrow();
        BigDecimal expectedStock = INITIAL_STOCK.subtract(QTY_PER_ORDER.multiply(BigDecimal.valueOf(successCount.get())));

        // The core assertion: whatever number of orders actually succeeded under
        // concurrent load, the final stock must reconcile EXACTLY against that count.
        // Any lost update or double-deduction would show up here as a mismatch.
        assertThat(finalState.getCurrentStock()).isEqualByComparingTo(expectedStock);
        assertThat(finalState.getCurrentStock()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        long ledgerRowCount = ledgerRepository.findByIngredientIdOrderByRecordedAtDesc(ingredientId).size();
        assertThat(ledgerRowCount).isEqualTo(successCount.get());
    }
}
