package com.rms.integration;

import com.rms.domain.*;
import com.rms.domain.enums.OrderStatus;
import com.rms.domain.enums.Role;
import com.rms.domain.enums.TableStatus;
import com.rms.domain.enums.UnitType;
import com.rms.dto.request.CreateOrderRequest;
import com.rms.dto.request.OrderItemRequest;
import com.rms.dto.request.UpdateOrderStatusRequest;
import com.rms.dto.response.AuthResponse;
import com.rms.dto.response.KitchenTicketMessage;
import com.rms.dto.response.OrderReadyNotification;
import com.rms.dto.response.OrderResponse;
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
 * IT-08 and IT-09: the two real-time signalling paths that replace the paper
 * KOT rail entirely - a broadcast to every kitchen terminal when a ticket is
 * created (IT-08), and a point-to-point push back to the specific waiter who
 * placed the order once the kitchen marks it ready (IT-09).
 */
class KitchenWebSocketIntegrationTest extends AbstractIntegrationTest {

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
                .username("it08-waiter").password(passwordEncoder.encode("password123"))
                .role(Role.WAITER).isActive(true).fullName("Test Waiter").build());

        Ingredient ingredient = ingredientRepository.save(Ingredient.builder()
                .name("IT08-Ingredient").currentStock(new BigDecimal("500"))
                .reorderLevel(new BigDecimal("50")).unitType(UnitType.KG)
                .averageUnitCost(BigDecimal.TEN).build());

        MenuItem dish = menuItemRepository.save(MenuItem.builder()
                .name("IT08-Dish").price(BigDecimal.valueOf(300)).category("Main Course").isAvailable(true).build());
        menuItemId = dish.getId();

        recipeRepository.save(Recipe.builder().menuItem(dish).ingredient(ingredient)
                .quantityRequired(new BigDecimal("50")).build());

        RestaurantTable table = tableRepository.save(RestaurantTable.builder()
                .tableNumber("IT08-T1").seatingCapacity(2).operationalStatus(TableStatus.AVAILABLE).build());
        tableId = table.getId();

        AuthResponse auth = restTemplate.postForObject(
                "/api/auth/login",
                new com.rms.dto.request.LoginRequest("it08-waiter", "password123"),
                AuthResponse.class);
        jwt = auth.token();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return headers;
    }

    @Test
    @DisplayName("IT-08: Submitting an order broadcasts a NEW_TICKET message on /topic/kitchen")
    void submitOrder_broadcastsNewTicketToKitchen() throws Exception {
        BlockingQueue<KitchenTicketMessage> received = new LinkedBlockingQueue<>();

        StompTestClient client = new StompTestClient();
        StompSession session = client.connect("http://localhost:" + port + "/ws", jwt);

        session.subscribe("/topic/kitchen", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return KitchenTicketMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((KitchenTicketMessage) payload);
            }
        });
        Thread.sleep(300);

        CreateOrderRequest request = new CreateOrderRequest(
                tableId, List.of(new OrderItemRequest(menuItemId, 1, null)), null);
        restTemplate.exchange("/api/orders", HttpMethod.POST, new HttpEntity<>(request, authHeaders()), OrderResponse.class);

        KitchenTicketMessage ticket = received.poll(10, TimeUnit.SECONDS);

        assertThat(ticket).as("expected a KitchenTicketMessage to arrive within 10 seconds").isNotNull();
        assertThat(ticket.eventType()).isEqualTo("NEW_TICKET");
        assertThat(ticket.tableNumber()).isEqualTo("IT08-T1");
        assertThat(ticket.order().status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("IT-09: Marking an order READY notifies the originating waiter point-to-point")
    void markOrderReady_notifiesOriginatingWaiter() throws Exception {
        // Submit the order first (plain REST - the WebSocket assertion here is scoped to
        // the READY notification, not the NEW_TICKET broadcast covered by IT-08).
        CreateOrderRequest createRequest = new CreateOrderRequest(
                tableId, List.of(new OrderItemRequest(menuItemId, 1, null)), null);
        var createResponse = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(createRequest, authHeaders()), OrderResponse.class);
        Long orderId = createResponse.getBody().id();

        BlockingQueue<OrderReadyNotification> received = new LinkedBlockingQueue<>();

        StompTestClient client = new StompTestClient();
        StompSession session = client.connect("http://localhost:" + port + "/ws", jwt);

        session.subscribe("/user/queue/order-ready", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return OrderReadyNotification.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((OrderReadyNotification) payload);
            }
        });
        Thread.sleep(300);

        // PENDING -> PREPARING -> READY
        restTemplate.exchange("/api/orders/" + orderId + "/status", HttpMethod.PATCH,
                new HttpEntity<>(new UpdateOrderStatusRequest(OrderStatus.PREPARING, null), authHeaders()), OrderResponse.class);
        restTemplate.exchange("/api/orders/" + orderId + "/status", HttpMethod.PATCH,
                new HttpEntity<>(new UpdateOrderStatusRequest(OrderStatus.READY, null), authHeaders()), OrderResponse.class);

        OrderReadyNotification notification = received.poll(10, TimeUnit.SECONDS);

        assertThat(notification).as("expected an OrderReadyNotification to arrive within 10 seconds").isNotNull();
        assertThat(notification.orderId()).isEqualTo(orderId);
        assertThat(notification.tableNumber()).isEqualTo("IT08-T1");
    }
}
