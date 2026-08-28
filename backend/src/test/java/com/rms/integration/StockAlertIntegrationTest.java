package com.rms.integration;

import com.rms.domain.*;
import com.rms.domain.enums.Role;
import com.rms.domain.enums.TableStatus;
import com.rms.domain.enums.UnitType;
import com.rms.dto.request.CreateOrderRequest;
import com.rms.dto.request.OrderItemRequest;
import com.rms.dto.response.AuthResponse;
import com.rms.dto.response.StockAlertMessage;
import com.rms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-06: a recipe deduction that crosses an ingredient reorder_level publishes a
 * real StockAlertMessage on /topic/alerts/stock, over a real STOMP connection -
 * this is the Manager Dashboard's live alert panel (Figure 3.10), verified from
 * the same client-side transport (SockJS/STOMP) the actual dashboard uses.
 */
class StockAlertIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private IngredientRepository ingredientRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private RecipeRepository recipeRepository;
    @Autowired private RestaurantTableRepository tableRepository;

    private Long tableId;
    private Long menuItemId;
    private String jwt;

    @BeforeEach
    void seedData() {
        userRepository.save(User.builder()
                .username("it06-waiter").password(passwordEncoder.encode("password123"))
                .role(Role.WAITER).isActive(true).fullName("Test Waiter").build());

        // reorderLevel=100; starting stock=230; one order deducts 150 -> lands at 80,
        // which is <= reorderLevel and therefore must trigger the alert.
        Ingredient chicken = ingredientRepository.save(Ingredient.builder()
                .name("IT06-Chicken").currentStock(new BigDecimal("230"))
                .reorderLevel(new BigDecimal("100")).unitType(UnitType.KG)
                .averageUnitCost(BigDecimal.valueOf(20)).build());

        MenuItem dish = menuItemRepository.save(MenuItem.builder()
                .name("IT06-Dish").price(BigDecimal.valueOf(500)).category("Main Course").isAvailable(true).build());
        menuItemId = dish.getId();

        recipeRepository.save(Recipe.builder().menuItem(dish).ingredient(chicken)
                .quantityRequired(new BigDecimal("150")).build());

        RestaurantTable table = tableRepository.save(RestaurantTable.builder()
                .tableNumber("IT06-T1").seatingCapacity(2).operationalStatus(TableStatus.AVAILABLE).build());
        tableId = table.getId();

        AuthResponse auth = restTemplate.postForObject(
                "/api/auth/login",
                new com.rms.dto.request.LoginRequest("it06-waiter", "password123"),
                AuthResponse.class);
        jwt = auth.token();
    }

    @Test
    @DisplayName("IT-06: Crossing the reorder level publishes a StockAlertMessage on /topic/alerts/stock")
    void reorderBreach_publishesStockAlert() throws Exception {
        BlockingQueue<StockAlertMessage> received = new LinkedBlockingQueue<>();

        StompTestClient client = new StompTestClient();
        StompSession session = client.connect("http://localhost:" + port + "/ws", jwt);

        session.subscribe("/topic/alerts/stock", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return StockAlertMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((StockAlertMessage) payload);
            }
        });

        // Give the broker a moment to register the subscription before triggering the event.
        Thread.sleep(300);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        CreateOrderRequest request = new CreateOrderRequest(
                tableId, List.of(new OrderItemRequest(menuItemId, 1, null)), null);
        restTemplate.exchange("/api/orders", HttpMethod.POST, new HttpEntity<>(request, headers), String.class);

        StockAlertMessage alert = received.poll(10, TimeUnit.SECONDS);

        assertThat(alert).as("expected a StockAlertMessage to arrive within 10 seconds").isNotNull();
        assertThat(alert.ingredientName()).isEqualTo("IT06-Chicken");
        assertThat(alert.severity()).isEqualTo("LOW_STOCK");
        assertThat(alert.currentStock()).isEqualByComparingTo("80");
    }
}
