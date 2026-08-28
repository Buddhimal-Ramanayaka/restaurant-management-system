-- ============================================================================
-- rms_eer_model.sql - DDL ONLY, for building the EER model in MySQL Workbench.
--
-- This is schema.sql with the seed data stripped out. It exists purely so that
-- Workbench's "Reverse Engineer MySQL Create Script" wizard has a clean, fast
-- input that yields a diagram of the structure alone - importing the full
-- schema.sql also works, but Workbench then parses ~430 lines of INSERT
-- statements it will only discard.
--
-- HOW TO PRODUCE THE EER DIAGRAM (MySQL Workbench 8.0):
--   File > Import > Reverse Engineer MySQL Create Script...
--   Browse to this file, tick "Place imported objects on a diagram", Execute >
--   Next > Finish. The EER canvas opens with all 20 tables and their foreign
--   key relationships already laid out.
--   Then: File > Save Model As... to write the .mwb file.
--
-- Tidy-up worth doing before exporting an image for the dissertation:
--   - Arrange > Autolayout to untangle the relationship lines
--   - drag the core triad (menu_items, recipes, ingredients) together, since
--     that is the relationship Figure 3.2 is really illustrating
--   - File > Export > Export as PNG / Single Page PDF
--
-- NOTE ON TABLE COUNT: this model contains 20 tables. The dissertation's
-- Figure 3.2 describes 19 - the twentieth, system_settings, was added later to
-- satisfy FR-21 (Admin-configurable service charge and VAT rates rather than
-- hardcoded literals). That deviation is disclosed, not accidental.
-- ============================================================================

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
