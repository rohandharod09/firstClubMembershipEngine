# FirstClub Membership Engine


---

## Features

- **Membership Plans** — Monthly (₹99), Quarterly (₹249), Yearly (₹899) plans
- **Tiered Membership** — Silver, Gold, Platinum tiers per plan with configurable pricing
- **Configurable Benefits** — Free delivery, percentage discounts, exclusive deals, early access, priority support (JSONB-driven, zero-code extensibility)
- **Eligibility Rules** — ORDER_COUNT, ORDER_VALUE, COHORT rules (Strategy pattern, AND/OR combinable)
- **Subscription Lifecycle** — Subscribe, Upgrade (immediate + prorated), Downgrade (scheduled to period-end), Cancel, Expire
- **State Machine** — 8-state validated subscription state machine (PENDING_PAYMENT → ACTIVE → UPGRADE_PENDING / DOWNGRADE_SCHEDULED / CANCELLED / GRACE_PERIOD → EXPIRED)
- **Checkout Integration** — `POST /api/v1/benefits/validate` computes real-time discounts and free delivery eligibility
- **Idempotency** — Every mutation endpoint requires an idempotency key, stored in DB with TTL
- **Concurrency Safety** — 4-layer defense: optimistic locking (`@Version`), partial unique index, application-level guard, idempotency dedup
- **Scheduled Jobs** — Expiry, Auto-renewal, Scheduled Downgrade Application, Tier Reevaluation (ShedLock for distributed locking)
- **Event-Driven** — Spring `@TransactionalEventListener` domain events, designed for Kafka migration
- **Redis Caching** — Plans catalog (5min TTL), active subscription lookup (30s TTL)
- **OpenAPI/Swagger** — Auto-generated API docs at `/swagger-ui.html`
- **Actuator** — Health, metrics, Prometheus endpoints

---

## Architecture

```
membership-engine/
├── api/               → REST controllers, DTOs, exception handler (Inbound Adapters)
├── application/       → Commands, queries, handlers, port interfaces (Use Cases)
├── domain/            → Pure Java: models, state machine, rule/benefit engines, events
├── infrastructure/    → JPA entities, Spring Data repos, Redis cache, event publisher
├── scheduler/         → ShedLock-protected batch jobs
├── config/            → Spring configuration (Redis, Scheduler, OpenAPI, Async)
└── common/            → Exceptions, injectable Clock
```

**Pattern**: Hexagonal (Ports & Adapters) — the domain layer has ZERO Spring/JPA dependencies. All framework concerns are in infrastructure.

**Design Patterns**: Strategy (rule/benefit engines), Composite (evaluators), State Machine (subscription lifecycle), Command/Query Separation, Repository, Domain Events, Template Method (schedulers), Idempotency Token.

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Database | PostgreSQL 16 |
| Cache | Redis (via Spring Data Redis) |
| Build | Gradle |
| API Docs | SpringDoc OpenAPI (Swagger) |
| Distributed Lock | ShedLock (JDBC provider) |
| Testing | JUnit 5, Mockito, Testcontainers |

---

## Prerequisites

- Java 21+
- PostgreSQL 16 (local)
- Redis (local, `127.0.0.1:6379`)
- Gradle (wrapper included)

---

## Setup Instructions

### 1. Install PostgreSQL (macOS)

```bash
# Install via Homebrew
brew install postgresql@16

# Start the service
brew services start postgresql@16

# Add to PATH (add to ~/.zshrc)
export PATH="/opt/homebrew/opt/postgresql@16/bin:$PATH"

# Create database and user
psql postgres -c "CREATE USER firstclub WITH PASSWORD 'firstclub';"
psql postgres -c "CREATE DATABASE firstclub_membership OWNER firstclub;"
psql postgres -c "GRANT ALL PRIVILEGES ON DATABASE firstclub_membership TO firstclub;"
psql postgres -c "GRANT ALL ON SCHEMA public TO firstclub;"

# Verify connection
psql -U firstclub -d firstclub_membership -c "SELECT 1;"
```

### 2. Start Redis

```bash
# If not already running
brew install redis
brew services start redis

# Verify
redis-cli ping  # should return PONG
```

### 3. Run the Application

```bash
# From the project root
./gradlew bootRun
```

The app starts on `http://localhost:8080`.

On startup, Spring Boot automatically:
1. Runs `schema.sql` — creates all tables, indexes, and constraints
2. Runs `data.sql` — seeds 3 plans, 9 tiers, and their benefits/eligibility rules

### 4. Access Swagger UI

Open: http://localhost:8080/swagger-ui.html

---

## API Reference

### Plans

#### `GET /api/v1/plans`
Returns all active membership plans with their tiers and benefits.

**Response 200:**
```json
{
  "plans": [
    {
      "id": "a1b2c3d4-0001-0001-0001-000000000001",
      "name": "Monthly",
      "durationDays": 30,
      "basePriceCents": 9900,
      "currency": "INR",
      "tiers": [
        {
          "id": "b1b2c3d4-0001-0001-0001-000000000001",
          "name": "Silver",
          "rank": 1,
          "priceCents": 0,
          "benefits": [
            { "type": "FREE_DELIVERY", "config": { "maxFreeDeliveriesPerMonth": 3, "minOrderValueCents": 19900 } },
            { "type": "PERCENTAGE_DISCOUNT", "config": { "discountPercent": 5, "applicableCategories": ["all"] } }
          ]
        }
      ]
    }
  ]
}
```

---

### Subscriptions

#### `POST /api/v1/subscriptions`
Subscribe to a plan tier.

**Request:**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "planId": "a1b2c3d4-0001-0001-0001-000000000001",
  "tierId": "b1b2c3d4-0001-0001-0001-000000000001",
  "autoRenew": true,
  "userCohort": "regular",
  "orderCount": 3,
  "totalOrderValueCents": 45000,
  "idempotencyKey": "client-uuid-v4-here"
}
```

**Response 201:**
```json
{
  "id": "uuid",
  "userId": "uuid",
  "planId": "uuid",
  "tierId": "uuid",
  "status": "ACTIVE",
  "startDate": "2026-05-23T00:00:00Z",
  "endDate": "2026-06-22T00:00:00Z",
  "autoRenew": true,
  "cancelledAt": null,
  "scheduledTierId": null,
  "createdAt": "2026-05-23T00:00:00Z",
  "updatedAt": "2026-05-23T00:00:00Z"
}
```

**Error 409** — User already has an active subscription or optimistic lock conflict  
**Error 422** — Eligibility not met (e.g., not enough orders for Gold tier)  
**Error 402** — Payment failed

---

#### `GET /api/v1/subscriptions/me?userId={userId}`
Get a user's active subscription.

**Response 200:** Same as subscribe response  
**Response 404:** No active subscription found

---

#### `POST /api/v1/subscriptions/{id}/upgrade`
Upgrade to a higher tier (immediate, prorated charge).

**Request:**
```json
{
  "targetTierId": "b1b2c3d4-0001-0001-0001-000000000002",
  "userCohort": "regular",
  "orderCount": 8,
  "totalOrderValueCents": 350000,
  "idempotencyKey": "upgrade-uuid-here"
}
```

**Response 200:** Updated subscription with new tierId

---

#### `POST /api/v1/subscriptions/{id}/downgrade`
Schedule a downgrade (takes effect at period end, not immediate).

**Request:**
```json
{
  "targetTierId": "b1b2c3d4-0001-0001-0001-000000000001",
  "idempotencyKey": "downgrade-uuid-here"
}
```

**Response 200:** Subscription with `status: DOWNGRADE_SCHEDULED`, `scheduledTierId` set

---

#### `POST /api/v1/subscriptions/{id}/cancel`
Cancel a subscription. Benefits remain active until `endDate`.

**Request:**
```json
{
  "userId": "uuid",
  "reason": "Too expensive",
  "idempotencyKey": "cancel-uuid-here"
}
```

**Response 200:** Subscription with `status: CANCELLED`

---

### Benefits

#### `POST /api/v1/benefits/validate`
Validate and calculate membership benefits for a checkout order.

**Request:**
```json
{
  "userId": "uuid",
  "orderId": "uuid",
  "orderValueCents": 50000,
  "orderCategories": ["dairy", "snacks"],
  "deliveryRequested": true
}
```

**Response 200:**
```json
{
  "eligible": true,
  "freeDelivery": true,
  "discountCents": 2500,
  "appliedBenefits": [
    { "type": "FREE_DELIVERY", "applied": true, "discountCents": 0, "description": "Free delivery applied." },
    { "type": "PERCENTAGE_DISCOUNT", "applied": true, "discountCents": 2500, "description": "5% discount applied: 2500 cents" }
  ],
  "membershipExpiresAt": "2026-06-22T00:00:00Z",
  "tierName": "Silver"
}
```

---

## Seed Data (from `data.sql`)

### Plans
| ID Suffix | Name | Duration | Price |
|-----------|------|----------|-------|
| `...000001` | Monthly | 30 days | ₹99 |
| `...000002` | Quarterly | 90 days | ₹249 |
| `...000003` | Yearly | 365 days | ₹899 |

### Monthly Plan Tiers (use these UUIDs for testing)
| Tier | UUID | Extra Price | Eligibility |
|------|------|-------------|-------------|
| Silver | `b1b2c3d4-0001-0001-0001-000000000001` | ₹0 | None |
| Gold | `b1b2c3d4-0001-0001-0001-000000000002` | ₹50 | 5+ orders OR ₹2000+ spend |
| Platinum | `b1b2c3d4-0001-0001-0001-000000000003` | ₹150 | 10+ orders AND ₹5000+ spend |

---

## Running Tests

```bash
# All tests (requires PostgreSQL + Redis running)
./gradlew test

# Unit tests only (no DB needed)
./gradlew test --tests "com.firstclub.membership.domain.*"
./gradlew test --tests "com.firstclub.membership.application.*"
./gradlew test --tests "com.firstclub.membership.concurrency.*"
```

---

## Key Engineering Decisions

### 1. Partial Unique Index (Concurrency Guard)
```sql
CREATE UNIQUE INDEX idx_one_active_sub_per_user
  ON user_subscriptions (user_id)
  WHERE status IN ('ACTIVE', 'GRACE_PERIOD', 'PENDING_PAYMENT', 'UPGRADE_PENDING', 'DOWNGRADE_SCHEDULED');
```
Even if two concurrent requests bypass the application check, the database enforces exactly one active subscription per user.

### 2. Optimistic Locking
`UserSubscription` has a `@Version` column. Concurrent updates return `409 Conflict`.

### 3. Idempotency
All mutation endpoints require an `idempotencyKey`. The system stores the response in `idempotency_records` (24-hour TTL). Duplicate calls return the cached response.

### 4. Downgrades Are Scheduled
Downgrades don't take immediate effect — they're applied at period end by the `ScheduledDowngradeJob`. This prevents benefit abuse (subscribe at Platinum, use all benefits, immediately downgrade).

### 5. JSONB for Extensibility
Benefits and eligibility rules store their config in JSONB. Adding a new benefit type requires zero schema changes — just implement `BenefitEvaluator`, annotate with `@Component`, insert a DB row.

### 6. FOR UPDATE SKIP LOCKED
Scheduler jobs use PostgreSQL's `SELECT ... FOR UPDATE SKIP LOCKED` to process expired subscriptions in parallel without row contention.

---

## Actuator Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Application health |
| `GET /actuator/info` | Build info |
| `GET /actuator/metrics` | All metrics |
| `GET /actuator/prometheus` | Prometheus-format metrics |
