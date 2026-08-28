-- ============================================================================
-- demo_data.sql - 14 days of historical operating data (2026-07-18 .. 2026-07-31)
--
-- PURPOSE: populate an otherwise-empty database with realistic trading history so
-- the Manager Dashboard's KPIs, 7-day revenue/COGS chart, top-selling-items table
-- and shift-reconciliation reports show meaningful figures instead of zeroes.
--
-- THIS IS GENERATED DEMONSTRATION DATA, NOT RECORDED OBSERVATIONS. It is safe to
-- show a system working against a realistic data volume; it must not be presented
-- as measurements taken from real users during a User Acceptance Test.
--
-- Run AFTER schema.sql, against a freshly created database:
--   mysql -u rms_user -prms_password rms_db < backend/src/main/resources/schema.sql
--   mysql -u rms_user -prms_password rms_db < backend/src/main/resources/demo_data.sql
--
-- STOCK NEUTRALITY: every recipe deduction across the 14 days is written to the
-- append-only inventory_ledger, and offsetting goods-received notes restore each
-- ingredient to EXACTLY its schema.sql seeded level. This keeps the ledger
-- forensically consistent while preserving the tuned demo thresholds (notably
-- Crab at 3.500kg against a 3.000kg reorder level).
-- ============================================================================

USE rms_db;

-- ----------------------------------------------------------------------------
-- Additional CRM customers (customer 1, Nadeeka Fernando/GOLD, comes from schema.sql)
-- ----------------------------------------------------------------------------
INSERT INTO customers (name, phone_number, visit_count, lifetime_spend, loyalty_tier) VALUES
  ('Ruwan Jayawardena', '0771112233', 9,  96500.00,  'SILVER'),
  ('Ayesha Silva',      '0762223344', 14, 178200.00, 'GOLD'),
  ('Dinesh Kumara',     '0713334455', 5,  41800.00,  'STANDARD'),
  ('Malithi Rajapaksa', '0784445566', 7,  73400.00,  'SILVER'),
  ('Tharindu Bandara',  '0755556677', 3,  22900.00,  'STANDARD'),
  ('Shanika Wickrama',  '0776667788', 11, 132600.00, 'GOLD'),
  ('Kasun Alwis',       '0727778899', 4,  31500.00,  'STANDARD');

DROP PROCEDURE IF EXISTS seed_demo_history;

DELIMITER $$
CREATE PROCEDURE seed_demo_history()
BEGIN
    DECLARE v_day        DATE DEFAULT '2026-07-18';
    DECLARE v_end        DATE DEFAULT '2026-07-31';
    DECLARE v_dow        INT;
    DECLARE v_orders     INT;
    DECLARE v_i          INT;
    DECLARE v_lines      INT;
    DECLARE v_j          INT;
    DECLARE v_created    DATETIME;
    DECLARE v_status     VARCHAR(12);
    DECLARE v_order_id   BIGINT;
    DECLARE v_cust       BIGINT;
    DECLARE v_qty        INT;
    DECLARE v_menu       BIGINT;
    DECLARE v_is_dinner  INT;

    WHILE v_day <= v_end DO
        SET v_dow = DAYOFWEEK(v_day);          -- 1 = Sunday, 7 = Saturday
        -- Weekends busier than weekdays, matching a suburban Colombo dine-in pattern.
        IF v_dow = 1 OR v_dow = 7 THEN
            SET v_orders = 24 + FLOOR(RAND() * 9);   -- 24-32
        ELSEIF v_dow = 6 THEN
            SET v_orders = 20 + FLOOR(RAND() * 7);   -- 20-26 (Friday)
        ELSE
            SET v_orders = 14 + FLOOR(RAND() * 7);   -- 14-20
        END IF;

        SET v_i = 1;
        WHILE v_i <= v_orders DO
            -- First ~45% of covers at lunch, remainder at dinner.
            SET v_is_dinner = IF(v_i > CEIL(v_orders * 0.45), 1, 0);
            IF v_is_dinner = 1 THEN
                SET v_created = TIMESTAMP(v_day, SEC_TO_TIME(18*3600 + FLOOR(RAND() * 12600)));  -- 18:00-21:30
            ELSE
                SET v_created = TIMESTAMP(v_day, SEC_TO_TIME(11*3600 + 1800 + FLOOR(RAND() * 10800))); -- 11:30-14:30
            END IF;

            -- ~2% of covers are voided (manager-authorised cancellation).
            SET v_status = IF(RAND() < 0.02, 'VOID', 'COMPLETED');

            -- ~40% of covers are matched to a CRM customer.
            IF RAND() < 0.40 THEN
                SET v_cust = 1 + FLOOR(RAND() * 8);
            ELSE
                SET v_cust = NULL;
            END IF;

            INSERT INTO orders (table_id, waiter_id, customer_id, status, created_at, updated_at, preparing_started_at)
            VALUES (1 + FLOOR(RAND() * 8), 3, v_cust, v_status, v_created,
                    v_created + INTERVAL (25 + FLOOR(RAND() * 40)) MINUTE,
                    v_created + INTERVAL (2 + FLOOR(RAND() * 6)) MINUTE);
            SET v_order_id = LAST_INSERT_ID();

            SET v_lines = 1 + FLOOR(RAND() * 4);   -- 1-4 distinct dishes per cover
            SET v_j = 1;
            WHILE v_j <= v_lines DO
                SET v_menu = (SELECT id FROM menu_items ORDER BY RAND() LIMIT 1);
                SET v_qty  = 1 + FLOOR(RAND() * 3);
                -- INSERT IGNORE-style guard: skip if this dish is already on the order
                IF NOT EXISTS (SELECT 1 FROM order_details WHERE order_id = v_order_id AND menu_item_id = v_menu) THEN
                    INSERT INTO order_details (order_id, menu_item_id, quantity, special_notes, line_status)
                    VALUES (v_order_id, v_menu, v_qty, NULL, v_status);
                END IF;
                SET v_j = v_j + 1;
            END WHILE;

            SET v_i = v_i + 1;
        END WHILE;

        SET v_day = v_day + INTERVAL 1 DAY;
    END WHILE;
END$$
DELIMITER ;

CALL seed_demo_history();
DROP PROCEDURE seed_demo_history;

-- ----------------------------------------------------------------------------
-- Shift reconciliation: two shifts per trading day, cash/card/digital totals
-- derived from that window's actual billed revenue (subtotal + 10% service + 8% VAT).
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS seed_demo_shifts;

DELIMITER $$
CREATE PROCEDURE seed_demo_shifts()
BEGIN
    DECLARE v_day      DATE DEFAULT '2026-07-18';
    DECLARE v_end      DATE DEFAULT '2026-07-31';
    DECLARE v_session  INT;
    DECLARE v_from     DATETIME;
    DECLARE v_to       DATETIME;
    DECLARE v_billed   DECIMAL(12,2);
    DECLARE v_cash     DECIMAL(12,2);
    DECLARE v_card     DECIMAL(12,2);
    DECLARE v_digital  DECIMAL(12,2);
    DECLARE v_var      DECIMAL(12,2);
    DECLARE v_reviewer BIGINT;

    WHILE v_day <= v_end DO
        SET v_session = 0;
        WHILE v_session <= 1 DO
            IF v_session = 0 THEN
                SET v_from = TIMESTAMP(v_day, '11:00:00');
                SET v_to   = TIMESTAMP(v_day, '15:30:00');
            ELSE
                SET v_from = TIMESTAMP(v_day, '17:30:00');
                SET v_to   = TIMESTAMP(v_day, '22:30:00');
            END IF;

            SELECT IFNULL(ROUND(SUM(od.quantity * mi.price) * 1.10 * 1.08, 2), 0)
              INTO v_billed
              FROM orders o
              JOIN order_details od ON od.order_id = o.id
              JOIN menu_items mi    ON mi.id = od.menu_item_id
             WHERE o.status = 'COMPLETED'
               AND o.created_at >= v_from AND o.created_at < v_to;

            SET v_cash    = ROUND(v_billed * 0.45, 2);
            SET v_card    = ROUND(v_billed * 0.40, 2);
            SET v_digital = ROUND(v_billed - v_cash - v_card, 2);

            -- Most drawers reconcile exactly; a few carry a small real-world discrepancy.
            SET v_var = CASE
                WHEN RAND() < 0.14 THEN ROUND((RAND() * 400) - 200, 2)
                ELSE 0.00
            END;

            -- The most recent three shifts are left unreviewed so the Manager
            -- "Mark Reviewed" action has something live to act on during a demo.
            SET v_reviewer = IF(v_day >= (v_end - INTERVAL 1 DAY), NULL, 2);

            INSERT INTO shifts (cashier_id, started_at, ended_at, system_cash_total,
                                system_card_total, system_digital_total,
                                declared_drawer_amount, variance, reviewed_by_manager_id)
            VALUES (5, v_from, v_to, v_cash, v_card, v_digital,
                    v_cash + v_var, v_var, v_reviewer);

            SET v_session = v_session + 1;
        END WHILE;
        SET v_day = v_day + INTERVAL 1 DAY;
    END WHILE;
END$$
DELIMITER ;

CALL seed_demo_shifts();
DROP PROCEDURE seed_demo_shifts;

-- ----------------------------------------------------------------------------
-- Inventory ledger: replay every completed order's recipe deduction in
-- chronological order, writing the append-only signed-delta rows the dissertation
-- (§3.2) specifies, then restore each ingredient to its seeded level via GRNs.
-- ----------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS seed_stock;
CREATE TEMPORARY TABLE seed_stock (
    ingredient_id BIGINT PRIMARY KEY,
    original      DECIMAL(12,3) NOT NULL,
    running       DECIMAL(12,3) NOT NULL
);
INSERT INTO seed_stock (ingredient_id, original, running)
SELECT id, current_stock, current_stock FROM ingredients;

DROP PROCEDURE IF EXISTS seed_demo_ledger;

DELIMITER $$
CREATE PROCEDURE seed_demo_ledger()
BEGIN
    DECLARE v_done   INT DEFAULT 0;
    DECLARE v_ing    BIGINT;
    DECLARE v_delta  DECIMAL(12,3);
    DECLARE v_at     DATETIME;
    DECLARE v_oid    BIGINT;
    DECLARE v_now    DECIMAL(12,3);

    DECLARE cur CURSOR FOR
        SELECT r.ingredient_id,
               ROUND(od.quantity * r.quantity_required, 3) AS qty_used,
               o.created_at,
               o.id
          FROM orders o
          JOIN order_details od ON od.order_id = o.id
          JOIN recipes r        ON r.menu_item_id = od.menu_item_id
         WHERE o.status = 'COMPLETED'
         ORDER BY o.created_at, o.id;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO v_ing, v_delta, v_at, v_oid;
        IF v_done = 1 THEN LEAVE read_loop; END IF;

        SELECT running INTO v_now FROM seed_stock WHERE ingredient_id = v_ing;

        -- Mid-period replenishment: if this deduction would drive the line negative,
        -- book a supplier delivery first (mirrors the app's own reorder-to-2x rule).
        IF v_now - v_delta < 0 THEN
            INSERT INTO inventory_ledger (ingredient_id, quantity_delta, resulting_stock, reason,
                                          reference_id, recorded_by_user_id, recorded_at)
            SELECT v_ing,
                   ROUND(i.reorder_level * 4, 3),
                   ROUND(v_now + i.reorder_level * 4, 3),
                   'GRN_RECEIPT', NULL, 2, v_at - INTERVAL 3 HOUR
              FROM ingredients i WHERE i.id = v_ing;

            SELECT ROUND(v_now + i.reorder_level * 4, 3) INTO v_now
              FROM ingredients i WHERE i.id = v_ing;
        END IF;

        SET v_now = ROUND(v_now - v_delta, 3);

        INSERT INTO inventory_ledger (ingredient_id, quantity_delta, resulting_stock, reason,
                                      reference_id, recorded_by_user_id, recorded_at)
        VALUES (v_ing, -v_delta, v_now, 'RECIPE_DEDUCTION', v_oid, 3, v_at);

        UPDATE seed_stock SET running = v_now WHERE ingredient_id = v_ing;
    END LOOP;
    CLOSE cur;
END$$
DELIMITER ;

CALL seed_demo_ledger();
DROP PROCEDURE seed_demo_ledger;

-- Final restocking delivery (31 Jul, pre-service) bringing every line back to its
-- seeded level, so the tuned live-demo thresholds still hold on day one.
INSERT INTO goods_received_notes (supplier_id, received_date, recorded_by_user_id, purchase_order_id)
SELECT DISTINCT i.preferred_supplier_id, '2026-07-31', 2, NULL
  FROM ingredients i
  JOIN seed_stock s ON s.ingredient_id = i.id
 WHERE i.preferred_supplier_id IS NOT NULL
   AND s.running <> s.original;

INSERT INTO grn_items (grn_id, ingredient_id, quantity_received, unit_cost)
SELECT g.id, i.id, ROUND(s.original - s.running, 3), i.average_unit_cost
  FROM ingredients i
  JOIN seed_stock s ON s.ingredient_id = i.id
  JOIN goods_received_notes g ON g.supplier_id = i.preferred_supplier_id
                             AND g.received_date = '2026-07-31'
 WHERE s.original > s.running;

INSERT INTO inventory_ledger (ingredient_id, quantity_delta, resulting_stock, reason,
                              reference_id, recorded_by_user_id, recorded_at)
SELECT s.ingredient_id, ROUND(s.original - s.running, 3), s.original, 'GRN_RECEIPT', NULL, 2,
       TIMESTAMP('2026-07-31', '08:15:00')
  FROM seed_stock s
 WHERE s.original > s.running;

UPDATE ingredients i
  JOIN seed_stock s ON s.ingredient_id = i.id
   SET i.current_stock = s.original;

-- ----------------------------------------------------------------------------
-- A little spoilage across the period, so the waste report is not empty.
-- ----------------------------------------------------------------------------
INSERT INTO waste_logs (ingredient_id, quantity_wasted, reason_code, logged_by_user_id, logged_at) VALUES
  (25, 0.400, 'SPOILAGE',     4, TIMESTAMP('2026-07-20', '15:10:00')),
  (20, 0.750, 'SPOILAGE',     4, TIMESTAMP('2026-07-23', '22:05:00')),
  (14, 0.250, 'PREP_ERROR',   4, TIMESTAMP('2026-07-26', '12:40:00')),
  (13, 6.000, 'BREAKAGE',     4, TIMESTAMP('2026-07-28', '19:20:00')),
  (26, 0.500, 'SPOILAGE',     4, TIMESTAMP('2026-07-30', '14:55:00'));

-- ----------------------------------------------------------------------------
-- TIMEZONE COMPENSATION - do not remove.
--
-- application.yml connects with `serverTimezone=UTC`, but this MySQL server runs on
-- SYSTEM time (Asia/Colombo, +05:30). The driver therefore treats a DATETIME as UTC
-- and hands the application a value shifted forward by the local offset. The app's
-- own writes round-trip consistently, but rows inserted directly by this script do
-- not: without this correction, a 19:00 dinner cover is read back by the dashboard
-- as 00:30 the following day, which both misdates the daily revenue totals and
-- makes evening trade appear as after-midnight trade.
--
-- Pre-subtracting the offset here means the application renders exactly the wall
-- clock times this script intends. Computed rather than hardcoded so the script
-- stays correct on a machine in a different timezone.
-- ----------------------------------------------------------------------------
SET @tz_offset_seconds = TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(), NOW());

UPDATE orders
   SET created_at           = created_at           - INTERVAL @tz_offset_seconds SECOND,
       updated_at           = updated_at           - INTERVAL @tz_offset_seconds SECOND,
       preparing_started_at = preparing_started_at - INTERVAL @tz_offset_seconds SECOND;

UPDATE shifts
   SET started_at = started_at - INTERVAL @tz_offset_seconds SECOND,
       ended_at   = ended_at   - INTERVAL @tz_offset_seconds SECOND;

UPDATE inventory_ledger
   SET recorded_at = recorded_at - INTERVAL @tz_offset_seconds SECOND;

UPDATE waste_logs
   SET logged_at = logged_at - INTERVAL @tz_offset_seconds SECOND;

DROP TEMPORARY TABLE IF EXISTS seed_stock;
