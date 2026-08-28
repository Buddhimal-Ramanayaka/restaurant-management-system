package com.rms.integration;

import com.rms.domain.RestaurantTable;
import com.rms.domain.User;
import com.rms.domain.enums.Role;
import com.rms.domain.enums.TableStatus;
import com.rms.dto.request.CreateOrderRequest;
import com.rms.dto.request.CreateReservationRequest;
import com.rms.dto.request.OrderItemRequest;
import com.rms.dto.response.AuthResponse;
import com.rms.dto.response.ReservationResponse;
import com.rms.repository.RestaurantTableRepository;
import com.rms.repository.UserRepository;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-10: a booked reservation flips its table to RESERVED, and a subsequent
 * walk-in attempt to open a POS session on that same table is rejected with
 * HTTP 409 - a waiter cannot silently seat a walk-in on a table that is
 * already promised to a reservation.
 */
class ReservationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RestaurantTableRepository tableRepository;

    private Long tableId;
    private String jwt;

    @BeforeEach
    void seedData() {
        userRepository.save(User.builder()
                .username("it10-waiter").password(passwordEncoder.encode("password123"))
                .role(Role.WAITER).isActive(true).fullName("Test Waiter").build());

        RestaurantTable table = tableRepository.save(RestaurantTable.builder()
                .tableNumber("IT10-T1").seatingCapacity(4).operationalStatus(TableStatus.AVAILABLE).build());
        tableId = table.getId();

        AuthResponse auth = restTemplate.postForObject(
                "/api/auth/login",
                new com.rms.dto.request.LoginRequest("it10-waiter", "password123"),
                AuthResponse.class);
        jwt = auth.token();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return headers;
    }

    @Test
    @DisplayName("IT-10: Booking a reservation blocks a subsequent walk-in POS open on that table")
    void reservation_blocksWalkInOpen() {
        CreateReservationRequest reservationRequest = new CreateReservationRequest(
                "Nadeesha Perera", "0771112233", tableId, LocalDateTime.now().plusHours(2), 4);

        var reservationResponse = restTemplate.exchange(
                "/api/reservations", HttpMethod.POST,
                new HttpEntity<>(reservationRequest, authHeaders()), ReservationResponse.class);

        assertThat(reservationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reservationResponse.getBody().status()).isEqualTo("BOOKED");

        RestaurantTable tableAfterBooking = tableRepository.findById(tableId).orElseThrow();
        assertThat(tableAfterBooking.getOperationalStatus()).isEqualTo(TableStatus.RESERVED);

        // A walk-in waiter now attempts to open the SAME table for an unrelated order.
        // The menu item id here does not need to reference a real row - openTableForOrder
        // runs BEFORE any menu item lookup in OrderService.submitOrder, so the table-lock
        // rejection fires first regardless. It only needs to pass CreateOrderRequest's own
        // @NotEmpty/@Valid bean validation so the request actually reaches that code path.
        CreateOrderRequest walkInRequest = new CreateOrderRequest(
                tableId, List.of(new OrderItemRequest(999999L, 1, null)), null);

        var walkInResponse = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(walkInRequest, authHeaders()), String.class);

        assertThat(walkInResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Table remains RESERVED - the walk-in attempt did not silently succeed.
        RestaurantTable tableAfterWalkInAttempt = tableRepository.findById(tableId).orElseThrow();
        assertThat(tableAfterWalkInAttempt.getOperationalStatus()).isEqualTo(TableStatus.RESERVED);
    }

    @Test
    @DisplayName("Checking in a reservation releases the table back to AVAILABLE for normal seating")
    void checkIn_releasesTableToAvailable() {
        CreateReservationRequest reservationRequest = new CreateReservationRequest(
                "Nadeesha Perera", "0771112233", tableId, LocalDateTime.now().plusHours(1), 2);
        var reservationResponse = restTemplate.exchange(
                "/api/reservations", HttpMethod.POST,
                new HttpEntity<>(reservationRequest, authHeaders()), ReservationResponse.class);
        Long reservationId = reservationResponse.getBody().id();

        var checkInResponse = restTemplate.exchange(
                "/api/reservations/" + reservationId + "/check-in", HttpMethod.PATCH,
                new HttpEntity<>(authHeaders()), ReservationResponse.class);

        assertThat(checkInResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(checkInResponse.getBody().status()).isEqualTo("CHECKED_IN");

        RestaurantTable tableAfterCheckIn = tableRepository.findById(tableId).orElseThrow();
        assertThat(tableAfterCheckIn.getOperationalStatus()).isEqualTo(TableStatus.AVAILABLE);
    }
}
