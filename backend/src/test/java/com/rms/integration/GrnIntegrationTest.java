package com.rms.integration;

import com.rms.domain.Ingredient;
import com.rms.domain.Supplier;
import com.rms.domain.User;
import com.rms.domain.enums.LedgerReason;
import com.rms.domain.enums.Role;
import com.rms.domain.enums.UnitType;
import com.rms.dto.request.GrnItemRequest;
import com.rms.dto.request.RecordGrnRequest;
import com.rms.dto.response.AuthResponse;
import com.rms.dto.response.GrnResponse;
import com.rms.repository.IngredientRepository;
import com.rms.repository.InventoryLedgerRepository;
import com.rms.repository.SupplierRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-03: GRN receipt updates stock and WAC through the real REST + Security
 * + database stack. Complements GrnServiceTest's UT-17 (which verifies the
 * arithmetic in isolation) by confirming the whole path - controller, service,
 * pessimistic-locked repository read, ledger append - works end to end.
 */
class GrnIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private IngredientRepository ingredientRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private InventoryLedgerRepository ledgerRepository;

    private Long ingredientId;
    private Long supplierId;
    private String jwt;

    @BeforeEach
    void seedData() {
        userRepository.save(User.builder()
                .username("it03-manager").password(passwordEncoder.encode("password123"))
                .role(Role.MANAGER).isActive(true).fullName("Test Manager").build());

        Ingredient rice = ingredientRepository.save(Ingredient.builder()
                .name("IT03-Rice").currentStock(new BigDecimal("50"))
                .reorderLevel(new BigDecimal("10")).unitType(UnitType.KG)
                .averageUnitCost(new BigDecimal("400")).build());
        ingredientId = rice.getId();

        Supplier supplier = supplierRepository.save(Supplier.builder().name("IT03-Supplier").build());
        supplierId = supplier.getId();

        AuthResponse auth = restTemplate.postForObject(
                "/api/auth/login",
                new com.rms.dto.request.LoginRequest("it03-manager", "password123"),
                AuthResponse.class);
        jwt = auth.token();
    }

    @Test
    @DisplayName("IT-03: Posting a GRN updates stock, recalculates WAC, and appends a ledger entry")
    void postGrn_updatesStockAndWac() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);

        RecordGrnRequest request = new RecordGrnRequest(
                supplierId, LocalDate.now(), null,
                List.of(new GrnItemRequest(ingredientId, new BigDecimal("20"), new BigDecimal("500")))
        );

        var response = restTemplate.exchange(
                "/api/grn", HttpMethod.POST, new HttpEntity<>(request, headers), GrnResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Ingredient afterGrn = ingredientRepository.findById(ingredientId).orElseThrow();
        assertThat(afterGrn.getCurrentStock()).isEqualByComparingTo("70"); // 50 + 20
        assertThat(afterGrn.getAverageUnitCost()).isEqualByComparingTo("428.5714"); // (50*400+20*500)/70

        var ledgerEntries = ledgerRepository.findByIngredientIdOrderByRecordedAtDesc(ingredientId);
        assertThat(ledgerEntries).hasSize(1);
        assertThat(ledgerEntries.get(0).getReason()).isEqualTo(LedgerReason.GRN_RECEIPT);
        assertThat(ledgerEntries.get(0).getQuantityDelta()).isEqualByComparingTo("20");
    }
}
