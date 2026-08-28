-- ============================================================================
-- Restaurant Management System - Authoritative Schema
--
-- This file is the source of truth for the database structure. The application
-- runs with spring.jpa.hibernate.ddl-auto=validate, meaning Hibernate will
-- refuse to start if the live schema does not match the JPA entity mappings -
-- so any change to an entity's @Column/@JoinColumn annotations must be
-- reflected here, and vice versa.
--
-- Run manually with:  mysql -u rms_user -p rms_db < schema.sql
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- USERS
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL,
    password    VARCHAR(255) NOT NULL,
    role        ENUM('ADMIN','MANAGER','WAITER','KITCHEN','CASHIER') NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    full_name   VARCHAR(100),
    created_at  DATETIME NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- SUPPLIERS  (referenced by ingredients, purchase_orders, goods_received_notes)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS suppliers (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    contact_phone  VARCHAR(20),
    contact_email  VARCHAR(100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- INGREDIENTS  -- the row this whole system revolves around locking correctly
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ingredients (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                  VARCHAR(100) NOT NULL,
    current_stock         DECIMAL(12,3) NOT NULL,
    reorder_level         DECIMAL(12,3) NOT NULL,
    unit_type             ENUM('KG','LITER','UNITS') NOT NULL,
    average_unit_cost     DECIMAL(12,4) NOT NULL DEFAULT 0,
    preferred_supplier_id BIGINT,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_ingredients_name UNIQUE (name),
    CONSTRAINT fk_ingredient_supplier FOREIGN KEY (preferred_supplier_id)
        REFERENCES suppliers(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_ingredients_reorder ON ingredients (current_stock, reorder_level);

-- ----------------------------------------------------------------------------
-- MENU_ITEMS
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS menu_items (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    price        DECIMAL(10,2) NOT NULL,
    category     VARCHAR(50) NOT NULL,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    image_url    VARCHAR(255),
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_menu_items_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_menu_items_category ON menu_items (category);

-- ----------------------------------------------------------------------------
-- RECIPES  -- join entity: the exact lookup the deduction engine walks
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS recipes (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    menu_item_id       BIGINT NOT NULL,
    ingredient_id      BIGINT NOT NULL,
    quantity_required  DECIMAL(12,3) NOT NULL,
    CONSTRAINT uk_recipe_menu_item_ingredient UNIQUE (menu_item_id, ingredient_id),
    CONSTRAINT fk_recipe_menu_item FOREIGN KEY (menu_item_id)
        REFERENCES menu_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_recipe_ingredient FOREIGN KEY (ingredient_id)
        REFERENCES ingredients(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_recipes_menu_item ON recipes (menu_item_id);
CREATE INDEX idx_recipes_ingredient ON recipes (ingredient_id);

-- ----------------------------------------------------------------------------
-- RESTAURANT_TABLES
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS restaurant_tables (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_number       VARCHAR(10) NOT NULL,
    seating_capacity   INT NOT NULL,
    operational_status ENUM('AVAILABLE','OCCUPIED','BILLED','CLEANING','RESERVED') NOT NULL DEFAULT 'AVAILABLE',
    current_order_id   BIGINT,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_tables_number UNIQUE (table_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- CUSTOMERS
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customers (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    phone_number    VARCHAR(15) NOT NULL,
    visit_count     INT NOT NULL DEFAULT 0,
    lifetime_spend  DECIMAL(12,2) NOT NULL DEFAULT 0,
    loyalty_tier    VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    CONSTRAINT uk_customers_phone UNIQUE (phone_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- ORDERS
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_id               BIGINT NOT NULL,
    waiter_id               BIGINT NOT NULL,
    customer_id            BIGINT,
    status                 ENUM('PENDING','PREPARING','READY','BILLED','COMPLETED','VOID') NOT NULL DEFAULT 'PENDING',
    created_at             DATETIME NOT NULL,
    updated_at             DATETIME NOT NULL,
    preparing_started_at   DATETIME,
    CONSTRAINT fk_order_waiter FOREIGN KEY (waiter_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_orders_table ON orders (table_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_waiter ON orders (waiter_id);

-- ----------------------------------------------------------------------------
-- ORDER_DETAILS
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_details (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id       BIGINT NOT NULL,
    menu_item_id   BIGINT NOT NULL,
    quantity       INT NOT NULL,
    special_notes  TEXT,
    line_status    ENUM('PENDING','PREPARING','READY','BILLED','COMPLETED','VOID') NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_order_detail_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_detail_menu_item FOREIGN KEY (menu_item_id) REFERENCES menu_items(id) ON DELETE RESTRICT,
    CONSTRAINT chk_order_detail_qty CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_order_details_order ON order_details (order_id);

-- ----------------------------------------------------------------------------
-- RESERVATIONS
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reservations (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_name     VARCHAR(100) NOT NULL,
    customer_phone    VARCHAR(15) NOT NULL,
    table_id          BIGINT NOT NULL,
    reservation_time  DATETIME NOT NULL,
    party_size        INT NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    CONSTRAINT fk_reservation_table FOREIGN KEY (table_id) REFERENCES restaurant_tables(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_reservations_table ON reservations (table_id);
CREATE INDEX idx_reservations_time ON reservations (reservation_time);

-- ----------------------------------------------------------------------------
-- INVENTORY_LEDGER  -- append-only, never UPDATE/DELETE
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS inventory_ledger (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    ingredient_id       BIGINT NOT NULL,
    quantity_delta      DECIMAL(12,3) NOT NULL,
    resulting_stock     DECIMAL(12,3) NOT NULL,
    reason              ENUM('RECIPE_DEDUCTION','GRN_RECEIPT','MANUAL_ADJUSTMENT','WASTE_SPOILAGE','STOCK_TAKE_CORRECTION') NOT NULL,
    reference_id        BIGINT,
    recorded_by_user_id BIGINT,
    recorded_at         DATETIME NOT NULL,
    CONSTRAINT fk_ledger_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_user FOREIGN KEY (recorded_by_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_ledger_ingredient ON inventory_ledger (ingredient_id, recorded_at);

-- ----------------------------------------------------------------------------
-- PURCHASE_ORDERS / PURCHASE_ORDER_ITEMS
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS purchase_orders (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_id      BIGINT NOT NULL,
    status           ENUM('DRAFT','PENDING_APPROVAL','APPROVED','ORDERED','RECEIVED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    auto_generated   BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by_user_id BIGINT,
    created_at       DATETIME NOT NULL,
    CONSTRAINT fk_po_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_po_approver FOREIGN KEY (approved_by_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_po_supplier_status ON purchase_orders (supplier_id, status);

CREATE TABLE IF NOT EXISTS purchase_order_items (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_order_id     BIGINT NOT NULL,
    ingredient_id         BIGINT NOT NULL,
    quantity_ordered      DECIMAL(12,3) NOT NULL,
    estimated_unit_cost   DECIMAL(12,4),
    CONSTRAINT fk_poi_purchase_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_poi_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- GOODS_RECEIVED_NOTES / GRN_ITEMS
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS goods_received_notes (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_id        BIGINT NOT NULL,
    received_date      DATE NOT NULL,
    recorded_by_user_id BIGINT,
    purchase_order_id  BIGINT,
    CONSTRAINT fk_grn_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_grn_recorder FOREIGN KEY (recorded_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_grn_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS grn_items (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    grn_id              BIGINT NOT NULL,
    ingredient_id       BIGINT NOT NULL,
    quantity_received   DECIMAL(12,3) NOT NULL,
    unit_cost           DECIMAL(12,4) NOT NULL,
    CONSTRAINT fk_grn_item_grn FOREIGN KEY (grn_id) REFERENCES goods_received_notes(id) ON DELETE CASCADE,
    CONSTRAINT fk_grn_item_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- AUDIT_LOGS  -- Module 2.8, populated exclusively by AuditLogAspect
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    action_type  VARCHAR(100) NOT NULL,
    ip_address   VARCHAR(45),
    details      TEXT,
    occurred_at  DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_audit_logs_user_time ON audit_logs (user_id, occurred_at);

-- ----------------------------------------------------------------------------
-- SHIFTS  -- Module 2.8 cashier reconciliation
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS shifts (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    cashier_id               BIGINT NOT NULL,
    started_at               DATETIME NOT NULL,
    ended_at                 DATETIME,
    system_cash_total        DECIMAL(12,2),
    system_card_total        DECIMAL(12,2),
    system_digital_total     DECIMAL(12,2),
    declared_drawer_amount   DECIMAL(12,2),
    variance                 DECIMAL(12,2),
    reviewed_by_manager_id   BIGINT,
    CONSTRAINT fk_shift_cashier FOREIGN KEY (cashier_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_reviewer FOREIGN KEY (reviewed_by_manager_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- WASTE_LOGS  -- Module 2.9
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS waste_logs (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    ingredient_id    BIGINT NOT NULL,
    quantity_wasted  DECIMAL(12,3) NOT NULL,
    reason_code      VARCHAR(30) NOT NULL,
    logged_by_user_id BIGINT NOT NULL,
    logged_at        DATETIME NOT NULL,
    CONSTRAINT fk_waste_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients(id) ON DELETE RESTRICT,
    CONSTRAINT fk_waste_user FOREIGN KEY (logged_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- PROMOTIONS  -- Module 2.10
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS promotions (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                     VARCHAR(100) NOT NULL,
    applies_to_category      VARCHAR(50),
    discount_percent         DECIMAL(5,2),
    buy_x_get_y_free         BOOLEAN,
    active_from              TIME,
    active_to                TIME,
    required_loyalty_tier    VARCHAR(20),
    enabled                  BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- SYSTEM_SETTINGS  -- FR-21: tax/service percentages configurable by Admin,
-- not hardcoded constants. Plain key-value store; the only two rows this app
-- currently reads are seeded below with the values BillingService/useCart.js
-- used as literals before this table existed, so default behaviour is unchanged.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS system_settings (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key   VARCHAR(50) NOT NULL UNIQUE,
    setting_value VARCHAR(50) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- SEED DATA
--
-- All five demo accounts below share the password "admin123". The stored value
-- is a real BCrypt hash at work factor 12, verified to round-trip against that
-- plaintext - CHANGE THESE IMMEDIATELY in any non-development environment.
-- ============================================================================
INSERT INTO users (username, password, role, is_active, full_name, created_at) VALUES
  ('admin',   '$2b$12$ggnnFL5UGQcD9cmI5bzd0uixnOGIHiKF2MVCpruvxoXUdrzzshcx6', 'ADMIN',   TRUE, 'System Administrator', NOW()),
  ('manager', '$2b$12$ggnnFL5UGQcD9cmI5bzd0uixnOGIHiKF2MVCpruvxoXUdrzzshcx6', 'MANAGER', TRUE, 'Dilshan Jayasinghe',   NOW()),
  ('waiter',  '$2b$12$ggnnFL5UGQcD9cmI5bzd0uixnOGIHiKF2MVCpruvxoXUdrzzshcx6', 'WAITER',  TRUE, 'Kamal Wijesinghe',     NOW()),
  ('kitchen', '$2b$12$ggnnFL5UGQcD9cmI5bzd0uixnOGIHiKF2MVCpruvxoXUdrzzshcx6', 'KITCHEN', TRUE, 'Kitchen Terminal 1',   NOW()),
  ('cashier', '$2b$12$ggnnFL5UGQcD9cmI5bzd0uixnOGIHiKF2MVCpruvxoXUdrzzshcx6', 'CASHIER', TRUE, 'Nimali Fernando',      NOW())
ON DUPLICATE KEY UPDATE username = username;

INSERT INTO system_settings (setting_key, setting_value) VALUES
  ('SERVICE_CHARGE_RATE', '0.10'),
  ('VAT_RATE', '0.08')
ON DUPLICATE KEY UPDATE setting_key = setting_key;

INSERT INTO restaurant_tables (table_number, seating_capacity, operational_status) VALUES
  ('T-01', 4, 'AVAILABLE'), ('T-02', 4, 'AVAILABLE'), ('T-03', 2, 'AVAILABLE'),
  ('T-04', 6, 'AVAILABLE'), ('T-05', 4, 'AVAILABLE'), ('T-06', 2, 'AVAILABLE'),
  ('T-07', 8, 'AVAILABLE'), ('T-08', 4, 'AVAILABLE')
ON DUPLICATE KEY UPDATE table_number = table_number;

INSERT INTO suppliers (name, contact_phone, contact_email) VALUES
  ('Lanka Agro Supplies',       '+94112345678', 'orders@lankaagro.lk'),
  ('Colombo Seafood Traders',   '+94112987654', 'sales@colomboseafood.lk'),
  ('Ceylon Dairy & Provisions', '+94114567890', 'info@ceylondairy.lk'),
  ('Colombo Bakery Supplies',   '+94112223344', 'orders@colombobakery.lk');

-- Ingredients for the real Daiya Food Restaurant menu (see menu_items below).
-- reorder_level is set close to current_stock on Crab and Cheese deliberately, so
-- a couple of test orders will trip the threshold and let you watch the live stock
-- alert and auto-drafted purchase order fire without having to hand-edit the
-- database first.
INSERT INTO ingredients (name, current_stock, reorder_level, unit_type, average_unit_cost, preferred_supplier_id) VALUES
  ('Basmati Rice',          60.000, 15.000, 'KG',     380.0000, 1),
  ('Kottu Roti (Godamba)',  40.000, 10.000, 'KG',     300.0000, 1),
  ('Egg Noodles',           25.000,  6.000, 'KG',     450.0000, 1),
  ('Pasta (Penne)',         15.000,  4.000, 'KG',     480.0000, 1),
  ('Spaghetti',             12.000,  3.000, 'KG',     500.0000, 1),
  ('Ramen Noodles',         10.000,  3.000, 'KG',     600.0000, 1),
  ('Chicken Breast',        35.000,  8.000, 'KG',     950.0000, 1),
  ('Beef',                  15.000,  4.000, 'KG',    1600.0000, 1),
  ('Prawns',                12.000,  3.000, 'KG',    2400.0000, 2),
  ('Crab',                   3.500,  3.000, 'KG',    2800.0000, 2),
  ('Cuttlefish',              8.000,  2.000, 'KG',    1800.0000, 2),
  ('Tuna Fish',              10.000,  3.000, 'KG',    1200.0000, 2),
  ('Eggs',                  300.000, 60.000, 'UNITS',   25.0000, 3),
  ('Cheese',                  4.000,  3.000, 'KG',    1800.0000, 3),
  ('Butter',                  6.000,  2.000, 'KG',    1500.0000, 3),
  ('Fresh Cream',             8.000,  2.000, 'LITER',  900.0000, 3),
  ('Milk',                   20.000,  5.000, 'LITER',  280.0000, 3),
  ('Ice Cream Base',         10.000,  3.000, 'LITER',  700.0000, 3),
  ('Onions',                 30.000,  6.000, 'KG',     180.0000, 1),
  ('Tomatoes',               20.000,  5.000, 'KG',     220.0000, 1),
  ('Garlic',                  8.000,  2.000, 'KG',     650.0000, 1),
  ('Cabbage',                10.000,  3.000, 'KG',     150.0000, 1),
  ('Carrots',                10.000,  3.000, 'KG',     200.0000, 1),
  ('Capsicum',                8.000,  2.000, 'KG',     400.0000, 1),
  ('Lettuce',                 5.000,  2.000, 'KG',     350.0000, 1),
  ('Cucumber',                8.000,  2.000, 'KG',     150.0000, 1),
  ('Potato',                 20.000,  5.000, 'KG',     280.0000, 1),
  ('Coconut Milk',           15.000,  4.000, 'LITER',  350.0000, 1),
  ('Soy Sauce',               8.000,  2.000, 'LITER',  600.0000, 1),
  ('Chili Sauce',             6.000,  2.000, 'LITER',  500.0000, 1),
  ('Tomato Ketchup',          6.000,  2.000, 'LITER',  450.0000, 1),
  ('Mayonnaise',              6.000,  2.000, 'LITER',  700.0000, 3),
  ('Curry Powder',            5.000,  1.000, 'KG',    1200.0000, 1),
  ('Cooking Oil',            25.000,  6.000, 'LITER',  720.0000, 1),
  ('Sugar',                  15.000,  4.000, 'KG',     260.0000, 1),
  ('Kithul Treacle',          5.000,  1.000, 'LITER',  900.0000, 1),
  ('Burger Buns',           100.000, 20.000, 'UNITS',   60.0000, 4),
  ('Sandwich Bread',         60.000, 15.000, 'UNITS',   50.0000, 4),
  ('Tortilla Wrap',          80.000, 20.000, 'UNITS',   45.0000, 4),
  ('Mango Pulp',             10.000,  3.000, 'LITER',  890.0000, 1),
  ('Watermelon',             10.000,  3.000, 'KG',     150.0000, 1),
  ('Mint Leaves',             2.000,  0.500, 'KG',     800.0000, 1),
  ('Soda Water',             15.000,  4.000, 'LITER',  200.0000, 1),
  ('Lime',                    6.000,  2.000, 'KG',     300.0000, 1),
  ('Blue Curacao Syrup',      3.000,  1.000, 'LITER', 1500.0000, 1);

-- Menu items - a representative selection spanning every section of the real Daiya
-- Food Restaurant menu (the restaurant that hosted this project's UAT - see
-- dissertation Acknowledgements / Chapter 5.5). The source menu prices dishes by
-- size/protein variant (e.g. Kottu Shovel Chicken x4/x6 vs Mix x4/x6); since
-- menu_items has one price per item, each dish below uses its base Chicken/Regular
-- variant rather than enumerating every combination.
INSERT INTO menu_items (name, price, category, is_available, image_url) VALUES
  ('Rice Kottu Shovel (Chicken)',        3000.00, 'Shovel',           TRUE, NULL),
  ('Double Spicy Kottu Shovel (Chicken)',5000.00, 'Shovel',           TRUE, NULL),
  ('Daiya Special Noodles',              2000.00, 'Noodles',          TRUE, NULL),
  ('Chicken Noodles',                    1500.00, 'Noodles',          TRUE, NULL),
  ('Seafood Noodles',                    1900.00, 'Noodles',          TRUE, NULL),
  ('Tomato and Onion Salad',              300.00, 'Salad',            TRUE, NULL),
  ('Caesar Salad',                       1500.00, 'Salad',            TRUE, NULL),
  ('Daiya Special Spicy Rice',           2000.00, 'Fried Rice',       TRUE, NULL),
  ('Chicken Rice',                       1200.00, 'Fried Rice',       TRUE, NULL),
  ('Vegetable Rice',                      800.00, 'Fried Rice',       TRUE, NULL),
  ('Seafood Rice',                       1500.00, 'Fried Rice',       TRUE, NULL),
  ('Egg Rice',                            900.00, 'Fried Rice',       TRUE, NULL),
  ('Chicken Kottu',                      1200.00, 'Kottu',            TRUE, NULL),
  ('Chicken Cheese Kottu',               1400.00, 'Kottu',            TRUE, NULL),
  ('Egg Kottu',                          1000.00, 'Kottu',            TRUE, NULL),
  ('Seafood Kottu',                      1500.00, 'Kottu',            TRUE, NULL),
  ('Chicken Curry with Coconut Sambol',  1800.00, 'Sri Lankan Curry', TRUE, NULL),
  ('Prawns Curry with Coconut Sambol',   2500.00, 'Sri Lankan Curry', TRUE, NULL),
  ('Crab Curry with Coconut Sambol',     3000.00, 'Sri Lankan Curry', TRUE, NULL),
  ('Deviled Chicken',                    1200.00, 'Bite Items',       TRUE, NULL),
  ('Deviled Prawns',                     2500.00, 'Bite Items',       TRUE, NULL),
  ('French Fries',                        800.00, 'Bite Items',       TRUE, NULL),
  ('Sri Lankan Omelet',                   600.00, 'Bite Items',       TRUE, NULL),
  ('Watalappan',                          400.00, 'Dessert',          TRUE, NULL),
  ('Ice Cream',                           300.00, 'Dessert',          TRUE, NULL),
  ('Crispy Chicken Burger',              2000.00, 'Burger',           TRUE, NULL),
  ('Beef Burger',                        2800.00, 'Burger',           TRUE, NULL),
  ('Chicken Wrap',                       1300.00, 'Wrap',             TRUE, NULL),
  ('Seafood Wrap',                       1900.00, 'Wrap',             TRUE, NULL),
  ('Club Sandwich',                      1200.00, 'Sandwich',         TRUE, NULL),
  ('Tuna Sandwich',                      1700.00, 'Sandwich',         TRUE, NULL),
  ('Chicken Egg Drop Soup',               800.00, 'Soup',             TRUE, NULL),
  ('Seafood Soup',                       1000.00, 'Soup',             TRUE, NULL),
  ('Cheese Pasta',                       1500.00, 'Pasta',            TRUE, NULL),
  ('Chicken and Cheese Pasta',           1700.00, 'Pasta',            TRUE, NULL),
  ('Spaghetti Carbonara',                1800.00, 'Spaghetti',        TRUE, NULL),
  ('Chicken Ramen',                      1300.00, 'Ramen',            TRUE, NULL),
  ('Seafood Ramen',                      1500.00, 'Ramen',            TRUE, NULL),
  ('Chicken Chopsuey',                   1500.00, 'Chopsuey',         TRUE, NULL),
  ('Seafood Chopsuey',                   1900.00, 'Chopsuey',         TRUE, NULL),
  ('Mango Juice',                         600.00, 'Fresh Juice',      TRUE, NULL),
  ('Watermelon Juice',                    400.00, 'Fresh Juice',      TRUE, NULL),
  ('Chocolate Milkshake',                 900.00, 'Milkshake',        TRUE, NULL),
  ('Vanilla Milkshake',                   800.00, 'Milkshake',        TRUE, NULL),
  ('Virgin Mojito',                       700.00, 'Mojito',           TRUE, NULL),
  ('Blue Lagoon Mojito',                  900.00, 'Mojito',           TRUE, NULL);

-- Recipe mappings: this is exactly what the Recipe Deduction Engine walks on every
-- order. Quantities are plausible per-serving amounts, not the restaurant's actual
-- (undisclosed) recipe costings. Several dishes deliberately share high-traffic
-- ingredients (Chicken Breast, Onions, Cooking Oil, Cabbage) so a mixed ticket
-- exercises the fold step (one lock, combined quantity) described in
-- RecipeDeductionService Phase 1.
INSERT INTO recipes (menu_item_id, ingredient_id, quantity_required)
SELECT m.id, i.id, r.qty FROM (
  SELECT 'Rice Kottu Shovel (Chicken)'         AS item, 'Kottu Roti (Godamba)' AS ing, 0.500 AS qty UNION ALL
  SELECT 'Rice Kottu Shovel (Chicken)',              'Chicken Breast',              0.250 UNION ALL
  SELECT 'Rice Kottu Shovel (Chicken)',              'Onions',                      0.080 UNION ALL
  SELECT 'Rice Kottu Shovel (Chicken)',              'Cabbage',                     0.080 UNION ALL
  SELECT 'Rice Kottu Shovel (Chicken)',              'Carrots',                     0.050 UNION ALL
  SELECT 'Rice Kottu Shovel (Chicken)',              'Eggs',                        2.000 UNION ALL
  SELECT 'Rice Kottu Shovel (Chicken)',              'Soy Sauce',                   0.030 UNION ALL
  SELECT 'Rice Kottu Shovel (Chicken)',              'Chili Sauce',                 0.020 UNION ALL
  SELECT 'Rice Kottu Shovel (Chicken)',              'Cooking Oil',                 0.040 UNION ALL

  SELECT 'Double Spicy Kottu Shovel (Chicken)',      'Kottu Roti (Godamba)',        0.500 UNION ALL
  SELECT 'Double Spicy Kottu Shovel (Chicken)',      'Chicken Breast',              0.280 UNION ALL
  SELECT 'Double Spicy Kottu Shovel (Chicken)',      'Onions',                      0.080 UNION ALL
  SELECT 'Double Spicy Kottu Shovel (Chicken)',      'Cabbage',                     0.080 UNION ALL
  SELECT 'Double Spicy Kottu Shovel (Chicken)',      'Chili Sauce',                 0.050 UNION ALL
  SELECT 'Double Spicy Kottu Shovel (Chicken)',      'Curry Powder',                0.030 UNION ALL
  SELECT 'Double Spicy Kottu Shovel (Chicken)',      'Eggs',                        2.000 UNION ALL
  SELECT 'Double Spicy Kottu Shovel (Chicken)',      'Cooking Oil',                 0.040 UNION ALL

  SELECT 'Daiya Special Noodles',                    'Egg Noodles',                 0.220 UNION ALL
  SELECT 'Daiya Special Noodles',                    'Chicken Breast',              0.100 UNION ALL
  SELECT 'Daiya Special Noodles',                    'Prawns',                      0.050 UNION ALL
  SELECT 'Daiya Special Noodles',                    'Cabbage',                     0.060 UNION ALL
  SELECT 'Daiya Special Noodles',                    'Carrots',                     0.040 UNION ALL
  SELECT 'Daiya Special Noodles',                    'Soy Sauce',                   0.020 UNION ALL
  SELECT 'Daiya Special Noodles',                    'Cooking Oil',                 0.030 UNION ALL
  SELECT 'Daiya Special Noodles',                    'Eggs',                        1.000 UNION ALL

  SELECT 'Chicken Noodles',                          'Egg Noodles',                 0.220 UNION ALL
  SELECT 'Chicken Noodles',                          'Chicken Breast',              0.150 UNION ALL
  SELECT 'Chicken Noodles',                          'Cabbage',                     0.050 UNION ALL
  SELECT 'Chicken Noodles',                          'Onions',                      0.040 UNION ALL
  SELECT 'Chicken Noodles',                          'Soy Sauce',                   0.020 UNION ALL
  SELECT 'Chicken Noodles',                          'Cooking Oil',                 0.030 UNION ALL

  SELECT 'Seafood Noodles',                          'Egg Noodles',                 0.220 UNION ALL
  SELECT 'Seafood Noodles',                          'Prawns',                      0.080 UNION ALL
  SELECT 'Seafood Noodles',                          'Cuttlefish',                  0.060 UNION ALL
  SELECT 'Seafood Noodles',                          'Cabbage',                     0.050 UNION ALL
  SELECT 'Seafood Noodles',                          'Soy Sauce',                   0.020 UNION ALL
  SELECT 'Seafood Noodles',                          'Cooking Oil',                 0.030 UNION ALL

  SELECT 'Tomato and Onion Salad',                   'Tomatoes',                    0.120 UNION ALL
  SELECT 'Tomato and Onion Salad',                   'Onions',                      0.080 UNION ALL
  SELECT 'Tomato and Onion Salad',                   'Cucumber',                    0.050 UNION ALL
  SELECT 'Tomato and Onion Salad',                   'Lettuce',                     0.020 UNION ALL

  SELECT 'Caesar Salad',                             'Lettuce',                     0.100 UNION ALL
  SELECT 'Caesar Salad',                             'Chicken Breast',              0.080 UNION ALL
  SELECT 'Caesar Salad',                             'Cheese',                      0.030 UNION ALL
  SELECT 'Caesar Salad',                             'Mayonnaise',                  0.020 UNION ALL

  SELECT 'Daiya Special Spicy Rice',                 'Basmati Rice',                0.250 UNION ALL
  SELECT 'Daiya Special Spicy Rice',                 'Chicken Breast',              0.120 UNION ALL
  SELECT 'Daiya Special Spicy Rice',                 'Prawns',                      0.050 UNION ALL
  SELECT 'Daiya Special Spicy Rice',                 'Onions',                      0.060 UNION ALL
  SELECT 'Daiya Special Spicy Rice',                 'Chili Sauce',                 0.030 UNION ALL
  SELECT 'Daiya Special Spicy Rice',                 'Curry Powder',                0.020 UNION ALL
  SELECT 'Daiya Special Spicy Rice',                 'Cooking Oil',                 0.030 UNION ALL
  SELECT 'Daiya Special Spicy Rice',                 'Eggs',                        1.000 UNION ALL

  SELECT 'Chicken Rice',                             'Basmati Rice',                0.250 UNION ALL
  SELECT 'Chicken Rice',                             'Chicken Breast',              0.150 UNION ALL
  SELECT 'Chicken Rice',                             'Onions',                      0.050 UNION ALL
  SELECT 'Chicken Rice',                             'Soy Sauce',                   0.015 UNION ALL
  SELECT 'Chicken Rice',                             'Cooking Oil',                 0.025 UNION ALL

  SELECT 'Vegetable Rice',                           'Basmati Rice',                0.250 UNION ALL
  SELECT 'Vegetable Rice',                           'Onions',                      0.050 UNION ALL
  SELECT 'Vegetable Rice',                           'Carrots',                     0.040 UNION ALL
  SELECT 'Vegetable Rice',                           'Capsicum',                    0.040 UNION ALL
  SELECT 'Vegetable Rice',                           'Cabbage',                     0.040 UNION ALL
  SELECT 'Vegetable Rice',                           'Cooking Oil',                 0.020 UNION ALL

  SELECT 'Seafood Rice',                             'Basmati Rice',                0.250 UNION ALL
  SELECT 'Seafood Rice',                             'Prawns',                      0.080 UNION ALL
  SELECT 'Seafood Rice',                             'Cuttlefish',                  0.050 UNION ALL
  SELECT 'Seafood Rice',                             'Onions',                      0.050 UNION ALL
  SELECT 'Seafood Rice',                             'Cooking Oil',                 0.030 UNION ALL

  SELECT 'Egg Rice',                                 'Basmati Rice',                0.250 UNION ALL
  SELECT 'Egg Rice',                                 'Eggs',                        2.000 UNION ALL
  SELECT 'Egg Rice',                                 'Onions',                      0.040 UNION ALL
  SELECT 'Egg Rice',                                 'Cooking Oil',                 0.020 UNION ALL

  SELECT 'Chicken Kottu',                            'Kottu Roti (Godamba)',        0.350 UNION ALL
  SELECT 'Chicken Kottu',                            'Chicken Breast',              0.180 UNION ALL
  SELECT 'Chicken Kottu',                            'Onions',                      0.060 UNION ALL
  SELECT 'Chicken Kottu',                            'Cabbage',                     0.060 UNION ALL
  SELECT 'Chicken Kottu',                            'Eggs',                        1.000 UNION ALL
  SELECT 'Chicken Kottu',                            'Soy Sauce',                   0.020 UNION ALL
  SELECT 'Chicken Kottu',                            'Cooking Oil',                 0.030 UNION ALL

  SELECT 'Chicken Cheese Kottu',                     'Kottu Roti (Godamba)',        0.350 UNION ALL
  SELECT 'Chicken Cheese Kottu',                     'Chicken Breast',              0.180 UNION ALL
  SELECT 'Chicken Cheese Kottu',                     'Cheese',                      0.080 UNION ALL
  SELECT 'Chicken Cheese Kottu',                     'Onions',                      0.060 UNION ALL
  SELECT 'Chicken Cheese Kottu',                     'Eggs',                        1.000 UNION ALL
  SELECT 'Chicken Cheese Kottu',                     'Cooking Oil',                 0.030 UNION ALL

  SELECT 'Egg Kottu',                                'Kottu Roti (Godamba)',        0.350 UNION ALL
  SELECT 'Egg Kottu',                                'Eggs',                        2.000 UNION ALL
  SELECT 'Egg Kottu',                                'Onions',                      0.060 UNION ALL
  SELECT 'Egg Kottu',                                'Cabbage',                     0.050 UNION ALL
  SELECT 'Egg Kottu',                                'Cooking Oil',                 0.030 UNION ALL

  SELECT 'Seafood Kottu',                            'Kottu Roti (Godamba)',        0.350 UNION ALL
  SELECT 'Seafood Kottu',                            'Prawns',                      0.080 UNION ALL
  SELECT 'Seafood Kottu',                            'Cuttlefish',                  0.060 UNION ALL
  SELECT 'Seafood Kottu',                            'Onions',                      0.060 UNION ALL
  SELECT 'Seafood Kottu',                            'Eggs',                        1.000 UNION ALL
  SELECT 'Seafood Kottu',                            'Cooking Oil',                 0.030 UNION ALL

  SELECT 'Chicken Curry with Coconut Sambol',        'Chicken Breast',              0.250 UNION ALL
  SELECT 'Chicken Curry with Coconut Sambol',        'Coconut Milk',                0.150 UNION ALL
  SELECT 'Chicken Curry with Coconut Sambol',        'Onions',                      0.060 UNION ALL
  SELECT 'Chicken Curry with Coconut Sambol',        'Curry Powder',                0.030 UNION ALL
  SELECT 'Chicken Curry with Coconut Sambol',        'Garlic',                      0.020 UNION ALL
  SELECT 'Chicken Curry with Coconut Sambol',        'Cooking Oil',                 0.020 UNION ALL

  SELECT 'Prawns Curry with Coconut Sambol',         'Prawns',                      0.200 UNION ALL
  SELECT 'Prawns Curry with Coconut Sambol',         'Coconut Milk',                0.150 UNION ALL
  SELECT 'Prawns Curry with Coconut Sambol',         'Onions',                      0.060 UNION ALL
  SELECT 'Prawns Curry with Coconut Sambol',         'Curry Powder',                0.030 UNION ALL
  SELECT 'Prawns Curry with Coconut Sambol',         'Garlic',                      0.020 UNION ALL

  SELECT 'Crab Curry with Coconut Sambol',           'Crab',                        0.350 UNION ALL
  SELECT 'Crab Curry with Coconut Sambol',           'Coconut Milk',                0.180 UNION ALL
  SELECT 'Crab Curry with Coconut Sambol',           'Onions',                      0.060 UNION ALL
  SELECT 'Crab Curry with Coconut Sambol',           'Curry Powder',                0.030 UNION ALL
  SELECT 'Crab Curry with Coconut Sambol',           'Garlic',                      0.020 UNION ALL

  SELECT 'Deviled Chicken',                          'Chicken Breast',              0.200 UNION ALL
  SELECT 'Deviled Chicken',                          'Onions',                      0.050 UNION ALL
  SELECT 'Deviled Chicken',                          'Capsicum',                    0.050 UNION ALL
  SELECT 'Deviled Chicken',                          'Chili Sauce',                 0.040 UNION ALL
  SELECT 'Deviled Chicken',                          'Tomato Ketchup',              0.020 UNION ALL
  SELECT 'Deviled Chicken',                          'Cooking Oil',                 0.030 UNION ALL

  SELECT 'Deviled Prawns',                           'Prawns',                      0.200 UNION ALL
  SELECT 'Deviled Prawns',                           'Onions',                      0.050 UNION ALL
  SELECT 'Deviled Prawns',                           'Capsicum',                    0.050 UNION ALL
  SELECT 'Deviled Prawns',                           'Chili Sauce',                 0.040 UNION ALL
  SELECT 'Deviled Prawns',                           'Tomato Ketchup',              0.020 UNION ALL
  SELECT 'Deviled Prawns',                           'Cooking Oil',                 0.030 UNION ALL

  SELECT 'French Fries',                             'Potato',                      0.300 UNION ALL
  SELECT 'French Fries',                             'Cooking Oil',                 0.050 UNION ALL
  SELECT 'French Fries',                             'Tomato Ketchup',              0.020 UNION ALL

  SELECT 'Sri Lankan Omelet',                        'Eggs',                        3.000 UNION ALL
  SELECT 'Sri Lankan Omelet',                        'Onions',                      0.030 UNION ALL
  SELECT 'Sri Lankan Omelet',                        'Cooking Oil',                 0.020 UNION ALL

  SELECT 'Watalappan',                                'Eggs',                       2.000 UNION ALL
  SELECT 'Watalappan',                                'Kithul Treacle',             0.080 UNION ALL
  SELECT 'Watalappan',                                'Coconut Milk',               0.100 UNION ALL
  SELECT 'Watalappan',                                'Sugar',                      0.020 UNION ALL

  SELECT 'Ice Cream',                                'Ice Cream Base',              0.150 UNION ALL
  SELECT 'Ice Cream',                                'Milk',                        0.050 UNION ALL
  SELECT 'Ice Cream',                                'Sugar',                       0.010 UNION ALL

  SELECT 'Crispy Chicken Burger',                    'Burger Buns',                 1.000 UNION ALL
  SELECT 'Crispy Chicken Burger',                    'Chicken Breast',              0.150 UNION ALL
  SELECT 'Crispy Chicken Burger',                    'Lettuce',                     0.030 UNION ALL
  SELECT 'Crispy Chicken Burger',                    'Tomato Ketchup',              0.020 UNION ALL
  SELECT 'Crispy Chicken Burger',                    'Mayonnaise',                  0.020 UNION ALL
  SELECT 'Crispy Chicken Burger',                    'Cooking Oil',                 0.030 UNION ALL

  SELECT 'Beef Burger',                              'Burger Buns',                 1.000 UNION ALL
  SELECT 'Beef Burger',                              'Beef',                       0.180 UNION ALL
  SELECT 'Beef Burger',                              'Cheese',                      0.030 UNION ALL
  SELECT 'Beef Burger',                              'Lettuce',                     0.030 UNION ALL
  SELECT 'Beef Burger',                              'Tomato Ketchup',              0.020 UNION ALL
  SELECT 'Beef Burger',                              'Cooking Oil',                 0.030 UNION ALL

  SELECT 'Chicken Wrap',                             'Tortilla Wrap',               1.000 UNION ALL
  SELECT 'Chicken Wrap',                             'Chicken Breast',              0.120 UNION ALL
  SELECT 'Chicken Wrap',                             'Lettuce',                     0.040 UNION ALL
  SELECT 'Chicken Wrap',                             'Mayonnaise',                  0.020 UNION ALL

  SELECT 'Seafood Wrap',                             'Tortilla Wrap',               1.000 UNION ALL
  SELECT 'Seafood Wrap',                             'Prawns',                      0.080 UNION ALL
  SELECT 'Seafood Wrap',                             'Cuttlefish',                  0.040 UNION ALL
  SELECT 'Seafood Wrap',                             'Lettuce',                     0.040 UNION ALL
  SELECT 'Seafood Wrap',                             'Mayonnaise',                  0.020 UNION ALL

  SELECT 'Club Sandwich',                            'Sandwich Bread',              2.000 UNION ALL
  SELECT 'Club Sandwich',                            'Chicken Breast',              0.100 UNION ALL
  SELECT 'Club Sandwich',                            'Eggs',                        1.000 UNION ALL
  SELECT 'Club Sandwich',                            'Lettuce',                     0.030 UNION ALL
  SELECT 'Club Sandwich',                            'Tomatoes',                    0.040 UNION ALL
  SELECT 'Club Sandwich',                            'Cucumber',                    0.030 UNION ALL
  SELECT 'Club Sandwich',                            'Mayonnaise',                  0.020 UNION ALL

  SELECT 'Tuna Sandwich',                            'Sandwich Bread',              2.000 UNION ALL
  SELECT 'Tuna Sandwich',                            'Tuna Fish',                   0.100 UNION ALL
  SELECT 'Tuna Sandwich',                            'Lettuce',                     0.030 UNION ALL
  SELECT 'Tuna Sandwich',                            'Mayonnaise',                  0.030 UNION ALL

  SELECT 'Chicken Egg Drop Soup',                    'Chicken Breast',              0.060 UNION ALL
  SELECT 'Chicken Egg Drop Soup',                    'Eggs',                        1.000 UNION ALL
  SELECT 'Chicken Egg Drop Soup',                    'Garlic',                      0.010 UNION ALL
  SELECT 'Chicken Egg Drop Soup',                    'Soy Sauce',                   0.010 UNION ALL

  SELECT 'Seafood Soup',                             'Prawns',                      0.050 UNION ALL
  SELECT 'Seafood Soup',                             'Cuttlefish',                  0.040 UNION ALL
  SELECT 'Seafood Soup',                             'Garlic',                      0.010 UNION ALL
  SELECT 'Seafood Soup',                             'Soy Sauce',                   0.010 UNION ALL

  SELECT 'Cheese Pasta',                             'Pasta (Penne)',               0.200 UNION ALL
  SELECT 'Cheese Pasta',                             'Cheese',                      0.100 UNION ALL
  SELECT 'Cheese Pasta',                             'Fresh Cream',                 0.080 UNION ALL
  SELECT 'Cheese Pasta',                             'Butter',                      0.020 UNION ALL

  SELECT 'Chicken and Cheese Pasta',                 'Pasta (Penne)',               0.200 UNION ALL
  SELECT 'Chicken and Cheese Pasta',                 'Chicken Breast',              0.120 UNION ALL
  SELECT 'Chicken and Cheese Pasta',                 'Cheese',                      0.100 UNION ALL
  SELECT 'Chicken and Cheese Pasta',                 'Fresh Cream',                 0.080 UNION ALL

  SELECT 'Spaghetti Carbonara',                      'Spaghetti',                   0.200 UNION ALL
  SELECT 'Spaghetti Carbonara',                      'Eggs',                        2.000 UNION ALL
  SELECT 'Spaghetti Carbonara',                      'Cheese',                      0.060 UNION ALL
  SELECT 'Spaghetti Carbonara',                      'Butter',                      0.020 UNION ALL
  SELECT 'Spaghetti Carbonara',                      'Fresh Cream',                 0.050 UNION ALL

  SELECT 'Chicken Ramen',                            'Ramen Noodles',               0.180 UNION ALL
  SELECT 'Chicken Ramen',                            'Chicken Breast',              0.120 UNION ALL
  SELECT 'Chicken Ramen',                            'Eggs',                        1.000 UNION ALL
  SELECT 'Chicken Ramen',                            'Garlic',                      0.015 UNION ALL
  SELECT 'Chicken Ramen',                            'Soy Sauce',                   0.020 UNION ALL

  SELECT 'Seafood Ramen',                            'Ramen Noodles',               0.180 UNION ALL
  SELECT 'Seafood Ramen',                            'Prawns',                      0.070 UNION ALL
  SELECT 'Seafood Ramen',                            'Cuttlefish',                  0.050 UNION ALL
  SELECT 'Seafood Ramen',                            'Eggs',                        1.000 UNION ALL
  SELECT 'Seafood Ramen',                            'Soy Sauce',                   0.020 UNION ALL

  SELECT 'Chicken Chopsuey',                         'Chicken Breast',              0.150 UNION ALL
  SELECT 'Chicken Chopsuey',                         'Cabbage',                     0.060 UNION ALL
  SELECT 'Chicken Chopsuey',                         'Carrots',                     0.040 UNION ALL
  SELECT 'Chicken Chopsuey',                         'Capsicum',                    0.040 UNION ALL
  SELECT 'Chicken Chopsuey',                         'Soy Sauce',                   0.020 UNION ALL
  SELECT 'Chicken Chopsuey',                         'Cooking Oil',                 0.020 UNION ALL

  SELECT 'Seafood Chopsuey',                         'Prawns',                      0.070 UNION ALL
  SELECT 'Seafood Chopsuey',                         'Cuttlefish',                  0.050 UNION ALL
  SELECT 'Seafood Chopsuey',                         'Cabbage',                     0.060 UNION ALL
  SELECT 'Seafood Chopsuey',                         'Carrots',                     0.040 UNION ALL
  SELECT 'Seafood Chopsuey',                         'Capsicum',                    0.040 UNION ALL
  SELECT 'Seafood Chopsuey',                         'Soy Sauce',                   0.020 UNION ALL

  SELECT 'Mango Juice',                              'Mango Pulp',                  0.200 UNION ALL
  SELECT 'Mango Juice',                              'Sugar',                       0.020 UNION ALL

  SELECT 'Watermelon Juice',                         'Watermelon',                  0.350 UNION ALL
  SELECT 'Watermelon Juice',                         'Sugar',                       0.010 UNION ALL

  SELECT 'Chocolate Milkshake',                      'Milk',                        0.200 UNION ALL
  SELECT 'Chocolate Milkshake',                      'Ice Cream Base',              0.100 UNION ALL
  SELECT 'Chocolate Milkshake',                      'Sugar',                       0.020 UNION ALL

  SELECT 'Vanilla Milkshake',                        'Milk',                        0.200 UNION ALL
  SELECT 'Vanilla Milkshake',                        'Ice Cream Base',              0.100 UNION ALL
  SELECT 'Vanilla Milkshake',                        'Sugar',                       0.015 UNION ALL

  SELECT 'Virgin Mojito',                            'Soda Water',                  0.200 UNION ALL
  SELECT 'Virgin Mojito',                            'Lime',                        0.060 UNION ALL
  SELECT 'Virgin Mojito',                            'Mint Leaves',                 0.010 UNION ALL
  SELECT 'Virgin Mojito',                            'Sugar',                       0.020 UNION ALL

  SELECT 'Blue Lagoon Mojito',                       'Soda Water',                  0.200 UNION ALL
  SELECT 'Blue Lagoon Mojito',                       'Lime',                        0.050 UNION ALL
  SELECT 'Blue Lagoon Mojito',                       'Mint Leaves',                 0.010 UNION ALL
  SELECT 'Blue Lagoon Mojito',                       'Blue Curacao Syrup',          0.030 UNION ALL
  SELECT 'Blue Lagoon Mojito',                       'Sugar',                       0.015
) AS r
JOIN menu_items  m ON m.name = r.item
JOIN ingredients i ON i.name = r.ing;

-- One enabled promotion so the Cashier bill preview has a discount rule to match on.
INSERT INTO promotions (name, applies_to_category, discount_percent, required_loyalty_tier, enabled)
VALUES ('Gold Tier 10% Off', NULL, 10.00, 'GOLD', TRUE);

-- A pre-registered GOLD customer so the POS CRM lookup (Figure 3.8) demonstrates a live
-- discount match immediately, without a brand-new customer having to earn tier status first.
INSERT INTO customers (name, phone_number, visit_count, lifetime_spend, loyalty_tier)
VALUES ('Nadeeka Fernando', '0770001234', 12, 145000.00, 'GOLD');
