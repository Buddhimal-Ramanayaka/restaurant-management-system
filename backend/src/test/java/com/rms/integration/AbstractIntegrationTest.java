package com.rms.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

/**
 * Shared base for every integration test. Boots a real, ephemeral MySQL 8.0
 * container per test class (Testcontainers manages the lifecycle) so that
 * pessimistic locking (SELECT ... FOR UPDATE) behaves EXACTLY as it does in
 * production - an H2 in-memory database would silently accept the @Lock
 * annotations without enforcing real row-level locking semantics, which would
 * make the concurrency test (IT-02) meaningless.
 *
 * REQUIRES DOCKER to be running on the machine executing `./gradlew test`.
 * If Docker is unavailable, these tests fail at container startup with a
 * clear "Could not find a valid Docker environment" message rather than a
 * cryptic connection-refused - that is Testcontainers' own behaviour, not
 * something this project needs to handle specially.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("rms_test")
            .withUsername("rms_test")
            .withPassword("rms_test");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // validate (not create/update) is deliberate - schema.sql is what actually
        // builds the schema, exactly mirroring production behaviour, and validate
        // then proves the JPA entity mappings still agree with it.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.sql.init.mode", () -> "always");
        // Deliberately NOT setting spring.jpa.defer-datasource-initialization=true.
        // Deferring would run schema.sql AFTER the EntityManagerFactory is built,
        // which means Hibernate's `validate` would run first against a completely
        // empty database and fail every time. Leaving it at the default (false)
        // runs schema.sql as part of DataSource initialisation - before JPA starts -
        // which is the ordering validate actually needs.
    }

    /**
     * Tables are wiped before every test method, in FK-safe order, then schema.sql's
     * seed rows are irrelevant to assertions because each test seeds exactly what it
     * needs with its own prefixed fixture names.
     *
     * This exists because @Container is static - one MySQL instance is shared across
     * every test method in a class - and TestRestTemplate calls hit a real servlet
     * container on a separate thread, so Spring's usual @Transactional rollback
     * cannot undo them. Without this reset, any test class with more than one @Test
     * method would fail on its second method with a duplicate-key violation the
     * moment its @BeforeEach re-inserted the same fixture usernames or table numbers.
     */
    @BeforeEach
    void resetDatabase() {
        List<String> tablesInDeletionOrder = List.of(
                "inventory_ledger", "order_details", "orders", "recipes",
                "grn_items", "goods_received_notes", "purchase_order_items", "purchase_orders",
                "waste_logs", "audit_logs", "shifts", "reservations",
                "menu_items", "ingredients", "restaurant_tables", "customers",
                "promotions", "suppliers", "users"
        );

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        tablesInDeletionOrder.forEach(table -> jdbcTemplate.execute("DELETE FROM " + table));
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }
}
