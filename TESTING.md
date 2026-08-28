# Testing Guide

## Backend (JUnit 5 + Mockito + Testcontainers)

### Unit tests (no external dependencies required)
39 tests across RecipeDeductionService, TableService, OrderService, JwtService,
GrnService, BillingService, InventoryAlertService, and PromotionService - mirrors
UT-01 through UT-18 from the dissertation Chapter 5 evaluation table, plus
additional edge-case coverage.

```bash
cd backend
./gradlew test --tests "com.rms.service.*" --tests "com.rms.security.*"
```

### Integration tests (REQUIRES DOCKER)
12 test methods across 8 test classes - mirrors IT-01 through IT-10. These boot a
real, ephemeral MySQL 8.0 container per test class via Testcontainers, so
pessimistic row locking is exercised against real InnoDB semantics rather than an
in-memory database that would not enforce it. Two tests additionally open real
STOMP-over-WebSocket connections (IT-06, IT-08, IT-09) to verify actual broadcast
delivery, using the same SockJS/STOMP transport the React frontend uses.

```bash
# Docker must be running first
cd backend
./gradlew test --tests "com.rms.integration.*"
```

If Docker is unavailable, these tests fail fast at container startup with a clear
message from Testcontainers rather than a hang or a cryptic connection error.

### Run everything
```bash
cd backend
./gradlew test
```

### Known simplifications (documented in code, not hidden)
- `BillingService` and `AnalyticsService` price lines off `MenuItem.getPrice()` at
  read time rather than a price snapshotted at order-time. Acceptable for a
  single-location SME with infrequent, announced price changes; flagged as future
  work for a system with frequent intra-shift repricing.
- `PromotionService` matches on loyalty tier and time window only - category-scoped
  promotions and buy-X-get-Y-free logic are not yet evaluated, though the
  `Promotion` entity already has the columns for it. It backs both `BillingService`
  (final settlement) and `GET /api/promotions/applicable` (the live discount
  preview in the waiter's cart), so the two are guaranteed to agree.

## Frontend (Vitest + React Testing Library)

42 tests across 7 files covering the cart reducer (`useCart`, including the
loyalty-discount-before-tax calculation), POS components (`ItemCard`,
`CategoryTabs`, `CartPanel`), the Kitchen `TicketCard` urgency-timer logic,
`AuthContext` session management, and `ProtectedRoute` role guarding.

```bash
cd frontend
npm install
npm test          # single run
npm run test:watch  # watch mode
```

All 42 frontend tests and all 39 backend unit tests pass on a clean checkout
(`./gradlew clean build` and `npm run build && npm test`), verified on the actual
Windows development/viva machine, not just in an isolated build sandbox. The 12
Testcontainers-based integration tests additionally require Docker Desktop to be
running and are not part of the default `bootRun` demo path.
