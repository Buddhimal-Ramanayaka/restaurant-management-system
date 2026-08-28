# Restaurant Management System (RMS)

Full-stack restaurant management system built for the IT5106 dissertation project.

## Structure

- `backend/` - Spring Boot 3.4.x / Java 21 / MySQL 8.0 / Gradle
- `frontend/` - React 19 / Vite / Tailwind CSS

## Quick Start

### 1. Database
```bash
mysql -u root -p -e "CREATE DATABASE rms_db CHARACTER SET utf8mb4; CREATE USER 'rms_user'@'localhost' IDENTIFIED BY 'rms_password'; GRANT ALL ON rms_db.* TO 'rms_user'@'localhost';"
mysql -u rms_user -p rms_db < backend/src/main/resources/schema.sql
```

### 2. Backend
```bash
cd backend
./gradlew build
java -jar build/libs/rms-backend-0.1.0.jar
```
Runs on http://localhost:8080. Default seed login: `admin` / `admin123` (change immediately).

### 3. Frontend
```bash
cd frontend
npm install
cp .env.example .env.local
npm run dev
```
Runs on http://localhost:5173.

## Architecture Highlights

- **Recipe Deduction Engine** (`backend/.../service/RecipeDeductionService.java`) - the core
  inventory engine. Pessimistic row locking (`SELECT ... FOR UPDATE`) prevents race conditions
  when concurrent orders share ingredients. Validate-before-mutate ensures a shortfall never
  leaves partial stock corruption.
- **WebSocket signalling** (`backend/.../websocket/`) - `OrderEventPublisher` is the single
  outbound broadcast gateway; `KitchenWebSocketController` handles inbound STOMP messages from
  the Kitchen Display. Topics: `/topic/kitchen`, `/topic/tables`, `/topic/alerts/stock`,
  `/user/{name}/queue/order-ready`.
- **Table state machine** (`backend/.../service/TableService.java`) - five states, pessimistic
  locking prevents two waiters opening the same table simultaneously.
- **Reservation workflow** (`backend/.../service/ReservationService.java`) - booking a
  reservation flips its table to RESERVED (blocking walk-ins); checking a guest in releases it
  back to AVAILABLE for normal seating.
- **Billing & Shift reconciliation** (`backend/.../service/BillingService.java`) - discount
  applied before service charge/VAT; shift cash/card/digital totals tracked per cashier session
  with declared-vs-system variance on close.
- **GRN & Weighted Average Costing** (`backend/.../service/GrnService.java`) - goods receipt
  recalculates each ingredient's average unit cost using the standard WAC formula, under the
  same pessimistic locking discipline as the deduction engine.
- **Purchase Order lifecycle** (`backend/.../service/PurchaseOrderService.java`) - manual and
  auto-drafted POs share one DRAFT → PENDING_APPROVAL → APPROVED → ORDERED → RECEIVED pipeline.
- **Analytics** (`backend/.../service/AnalyticsService.java`) - daily sales vs. COGS, top-selling
  items, and customer visit frequency, computed read-only from committed order data.
- **Audit trail** (`backend/.../aspect/AuditLogAspect.java`) - AOP interceptor, fires on
  `@AuditableAction`-annotated service methods only (stock corrections, recipe edits, voids,
  PO approvals, GRN receipts, shift closes, reservation actions).
- **Frontend real-time flow** (`frontend/src/pages/KitchenPage.jsx`) - one initial REST fetch,
  then all updates merge into local state via STOMP - no polling, no full-board refetch.

## Setup

- **Windows:** see [SETUP_WINDOWS.md](SETUP_WINDOWS.md)
- **macOS / Linux:** see [SETUP_MACOS_LINUX.md](SETUP_MACOS_LINUX.md)

## Testing

See [TESTING.md](TESTING.md) for full instructions. Summary:
- **Backend**: 26 unit tests (Mockito, no external deps) + 12 integration test methods
  (Testcontainers MySQL + real STOMP connections - requires Docker). Written and manually
  reviewed but not executable in the environment this codebase was generated in (no Docker, no
  Maven Central access) - run `./gradlew test` on a normal dev machine to execute them.
- **Frontend**: 39 tests (Vitest + React Testing Library), covering the cart reducer, POS
  components, Kitchen ticket urgency logic, auth session state, and route guarding. **Actually
  run and passing** (`npm test`) in the environment this codebase was generated in.

See the full dissertation for detailed design rationale, diagrams, and test results.
