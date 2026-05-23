# FirstClub Membership Engine - Technical Deep Dive

**Purpose**: This document explains every architectural decision, design pattern, and engineering trade-off in the Membership Engine. It is structured as a Q&A reference for interview preparation - every section answers the "what", "why", and "what could go wrong" for each component.

---

## Table of Contents

1. [Architecture: Why Hexagonal?](#1-architecture-why-hexagonal)
2. [Database Entity Design: Every Table Explained](#2-database-entity-design-every-table-explained)
3. [Why PostgreSQL Over MySQL](#3-why-postgresql-over-mysql)
4. [Why JSONB Instead of Separate Tables](#4-why-jsonb-instead-of-separate-tables)
5. [Subscription Lifecycle State Machine](#5-subscription-lifecycle-state-machine)
6. [Concurrency Handling: The Full Picture](#6-concurrency-handling-the-full-picture)
7. [Idempotency: Why, Where, and How](#7-idempotency-why-where-and-how)
8. [Transaction Boundaries: What Goes Where](#8-transaction-boundaries-what-goes-where)
9. [Rule Engine: Eligibility Evaluation](#9-rule-engine-eligibility-evaluation)
10. [Benefit Engine: How Discounts Actually Work](#10-benefit-engine-how-discounts-actually-work)
11. [Event-Driven Design](#11-event-driven-design)
12. [Scheduler Design: Background Jobs](#12-scheduler-design-background-jobs)
13. [Caching Strategy](#13-caching-strategy)
14. [Edge Cases and How We Handle Them](#14-edge-cases-and-how-we-handle-them)
15. [Indexing Strategy: Why Each Index Exists](#15-indexing-strategy-why-each-index-exists)
16. [Error Handling Strategy](#16-error-handling-strategy)
17. [API Design Decisions](#17-api-design-decisions)
18. [Domain vs JPA Entity Separation](#18-domain-vs-jpa-entity-separation)
19. [Testing Strategy](#19-testing-strategy)
20. [What Would Change at Scale](#20-what-would-change-at-scale)

---

## 1. Architecture: Why Hexagonal?

### What is Hexagonal Architecture?

Also called "Ports & Adapters". The core idea: the **domain** (business logic) sits at the center and has no knowledge of the outside world. It communicates through **ports** (interfaces). The outside world connects through **adapters** (implementations).

```
                    +--------------------------+
                    |      REST Controller     |  <-- Inbound Adapter
                    +-----------+--------------+
                                |
                    +-----------v--------------+
                    |    Application Layer      |  <-- Use Cases / Orchestration
                    |   (Command/Query Handlers)|
                    +-----------+--------------+
                                |
               +----------------v-----------------+
               |         Domain Layer              |  <-- Pure Business Logic
               |  (Entities, Services, Rules,      |
               |   Benefits, State Machine)        |
               +----------------+-----------------+
                                |
                    +-----------v--------------+
                    |    Port Interfaces        |  <-- Contracts
                    | (SubscriptionRepository,  |
                    |  PaymentGateway, etc.)    |
                    +-----------+--------------+
                                |
                    +-----------v--------------+
                    |   Infrastructure Layer    |  <-- Outbound Adapters
                    | (JPA Repos, Mock Payment, |
                    |  Spring Events, Cache)    |
                    +--------------------------+
```

### Why not just a standard layered architecture (Controller -> Service -> Repository)?

**Interview answer**: In a standard layered architecture, your domain logic depends on your infrastructure. If you change your database from PostgreSQL to MongoDB, or your payment gateway from Stripe to Razorpay, you'd need to modify your business logic layer. In hexagonal architecture, the domain defines the contract (port), and infrastructure implements it (adapter). You swap adapters without touching domain code.

**Concrete example in our system**: The `PaymentGateway` is a port interface in the application layer. Right now, `MockPaymentGateway` implements it. Tomorrow, `StripePaymentGateway` or `RazorpayPaymentGateway` can implement the same interface. The subscription flow doesn't change at all.

### Why "Modular Monolith" and not Microservices?

For an SDE-2 assignment, microservices add operational complexity (service discovery, distributed transactions, network failures) without proportional benefit. A modular monolith gives us:
- Clear module boundaries (same as microservice boundaries)
- Single deployment unit (simpler)
- In-process communication (faster, no network overhead)
- Easy to extract into microservices later because module boundaries are already clean

**The key insight**: If your monolith has clean boundaries, extracting a microservice is a deployment change, not an architectural rewrite.

---

## 2. Database Entity Design: Every Table Explained

### 2.1 `membership_plans`

**What it stores**: The subscription plan types (Monthly, Quarterly, Yearly).

| Column | Type | Why |
|--------|------|-----|
| `id` | `UUID` | Avoids sequential ID guessing, safe for distributed systems |
| `name` | `VARCHAR(100)` | Human-readable plan name |
| `duration_days` | `INT` | Plan length in days, not months, because months have variable lengths. 30/90/365 is unambiguous |
| `base_price_cents` | `BIGINT` | **Cents, not rupees**. Avoids floating-point precision errors. Rs 99.00 = 9900 cents |
| `currency` | `VARCHAR(3)` | ISO 4217 code. Supports multi-currency in the future |
| `active` | `BOOLEAN` | Soft-delete. Deactivating a plan doesn't break existing subscriptions referencing it |

**Why UUID instead of auto-increment?**
- Auto-increment IDs leak business information (competitor can infer how many plans you have).
- Auto-increment IDs cause contention in distributed databases (sequence generation becomes a bottleneck).
- UUIDs are safe for future service extraction (no cross-service ID collision).

**Why `duration_days` instead of an enum like MONTHLY/QUARTERLY/YEARLY?**
- What if business wants a "14-day trial"? Or a "6-month plan"? `duration_days` supports any duration without code changes.
- The names "Monthly", "Quarterly", "Yearly" are just display labels stored in `name`. The actual duration is data-driven.

**Why `base_price_cents` as BIGINT?**
- `DECIMAL`/`NUMERIC` types invite floating-point operations in application code.
- Cents as integers make arithmetic exact: `9900 + 5000 = 14900` (no rounding issues).
- Stripe, Razorpay, and most payment gateways use cents/paise internally.

### 2.2 `membership_tiers`

**What it stores**: Tiers within a plan (Silver, Gold, Platinum).

| Column | Type | Why |
|--------|------|-----|
| `id` | `UUID` | Primary key |
| `plan_id` | `UUID FK` | Which plan this tier belongs to |
| `name` | `VARCHAR(50)` | Tier display name |
| `rank` | `INT` | Ordering for comparison. Silver=1, Gold=2, Platinum=3 |
| `price_cents` | `BIGINT` | Surcharge over the plan's base price |
| `active` | `BOOLEAN` | Soft-delete |

**Why is `rank` an integer?**

This is critical for upgrade/downgrade logic. When a user requests a tier change, we compare `rank` values:
- `targetTier.rank > currentTier.rank` = upgrade
- `targetTier.rank < currentTier.rank` = downgrade
- `targetTier.rank == currentTier.rank` = invalid (same tier)

Without `rank`, we'd need hardcoded tier ordering in code. With `rank`, adding a "Diamond" tier (rank=4) between Platinum and a future tier requires only a database insert.

**Why `price_cents` on the tier and `base_price_cents` on the plan?**

Total cost = `plan.base_price_cents + tier.price_cents`. This lets us price tiers independently of the plan. Silver might have 0 surcharge, Gold might add 5000, Platinum might add 15000. If business changes the base price of the Monthly plan, all tiers are adjusted automatically.

**Why is the tier tied to a plan (plan_id FK)?**

Different plans can have different tier pricing. Silver on Monthly might cost differently than Silver on Yearly. This gives maximum pricing flexibility.

### 2.3 `tier_benefits`

**What it stores**: Benefits granted by each tier (free delivery, discounts, etc.).

| Column | Type | Why |
|--------|------|-----|
| `id` | `UUID` | Primary key |
| `tier_id` | `UUID FK` | Which tier grants this benefit |
| `benefit_type` | `VARCHAR(50)` | Benefit type key (e.g., `FREE_DELIVERY`, `PERCENTAGE_DISCOUNT`) |
| `config_json` | `JSONB` | Benefit parameters as JSON |
| `active` | `BOOLEAN` | Toggle individual benefits without deletion |

**Why JSONB for config instead of typed columns?**

This is arguably the most important design decision in the schema. Consider the alternatives:

**Option A - Typed columns**:
```sql
CREATE TABLE tier_benefits (
    discount_percent DECIMAL,
    max_free_deliveries INT,
    applicable_categories TEXT[],
    early_access_hours INT,
    ...
);
```
Problem: Every new benefit type requires a schema migration. Most rows would have mostly-NULL columns (sparse matrix). Adding "lounge access" means ALTER TABLE across production.

**Option B - Entity-Attribute-Value (EAV)**:
```sql
CREATE TABLE benefit_params (
    benefit_id UUID,
    param_key VARCHAR,
    param_value VARCHAR
);
```
Problem: Everything is a string. No type safety. Querying is verbose (joins per parameter). No nested structures.

**Option C - JSONB (our choice)**:
```sql
config_json JSONB
-- Example: {"discountPercent": 10, "applicableCategories": ["dairy", "snacks"], "maxOrderValue": 100000}
-- Example: {"maxFreeDeliveries": 5, "minOrderValueCents": 20000}
```
Benefits:
- Schema-free: new benefit types need zero DDL changes.
- Each benefit type has its own config structure, interpreted by its evaluator.
- JSONB is indexable in PostgreSQL (GIN indexes) if we ever need to query inside the JSON.
- Supports nested structures (arrays, objects) natively.
- Type safety is enforced at the application level by the `BenefitEvaluator` strategy.

**Interview follow-up: "Doesn't JSONB lose type safety?"**

Yes, at the database level. But we enforce structure at the application level. Each `BenefitEvaluator` implementation knows what config shape it expects and validates it. This is an acceptable trade-off because:
1. Benefits are admin-configured, not user-generated (lower volume, can be validated on write).
2. The evaluator fails fast with a clear error if config is malformed.
3. The extensibility gain (new benefit type = new class + DB row, zero migration) far outweighs the type safety loss.

### 2.4 `tier_eligibility_rules`

**What it stores**: Rules that determine if a user qualifies for a tier.

| Column | Type | Why |
|--------|------|-----|
| `id` | `UUID` | Primary key |
| `tier_id` | `UUID FK` | Which tier this rule guards |
| `rule_type` | `VARCHAR(50)` | Rule type key (e.g., `ORDER_COUNT`, `ORDER_VALUE`, `COHORT`) |
| `config_json` | `JSONB` | Rule parameters |
| `operator` | `VARCHAR(3)` | `AND` / `OR` — how this rule combines with others for the same tier |

**Why the `operator` column?**

A tier might have multiple eligibility rules. For example, Gold tier requires:
- At least 10 orders in the last 30 days (ORDER_COUNT)
- AND total order value >= Rs 5000 (ORDER_VALUE)

Or a promotional tier might accept:
- 10 orders in the last 30 days (ORDER_COUNT)
- OR belong to "early_adopter" cohort (COHORT)

The `operator` field lets us configure AND/OR logic per rule without code changes.

**How the evaluator combines rules**:
1. Group rules by operator.
2. All AND-group rules must pass.
3. At least one OR-group rule must pass.
4. If both groups exist, both groups must satisfy their respective conditions.

### 2.5 `user_subscriptions` (The Most Critical Table)

**What it stores**: A user's active (or historical) subscription.

| Column | Type | Why |
|--------|------|-----|
| `id` | `UUID` | Primary key |
| `user_id` | `UUID` | Which user owns this subscription |
| `plan_id` | `UUID FK` | Which plan |
| `tier_id` | `UUID FK` | Which tier |
| `status` | `VARCHAR(30)` | Current lifecycle state |
| `start_date` | `TIMESTAMP WITH TIME ZONE` | When the subscription started |
| `end_date` | `TIMESTAMP WITH TIME ZONE` | When it expires |
| `auto_renew` | `BOOLEAN` | Should it auto-renew on expiry? |
| `cancelled_at` | `TIMESTAMP WITH TIME ZONE` | When cancellation was requested (nullable) |
| `previous_tier_id` | `UUID` | The tier before an upgrade/downgrade (nullable) |
| `scheduled_tier_id` | `UUID` | The tier to apply on downgrade completion (nullable) |
| `version` | `BIGINT` | Optimistic lock version counter |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | Audit |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | Audit |

**Why `TIMESTAMP WITH TIME ZONE` and not `DATE` or `TIMESTAMP`?**
- `DATE` loses time precision (subscription expires at midnight? which timezone's midnight?).
- `TIMESTAMP` without timezone is ambiguous — it depends on the server's timezone setting.
- `TIMESTAMP WITH TIME ZONE` stores everything in UTC and converts on read. Unambiguous, portable.

**Why `previous_tier_id`?**

Rollback safety. If a user upgrades from Silver to Gold but the payment fails mid-transaction, we need to know what to roll back to. `previous_tier_id` records the "before" state.

**Why `scheduled_tier_id`?**

For deferred downgrades. When a user requests a downgrade, we don't apply it immediately (that would be exploitable). We store the target tier in `scheduled_tier_id` and set status to `DOWNGRADE_SCHEDULED`. A scheduler job applies it at `end_date`.

**Why `version` (optimistic locking)?**

This is the primary concurrency guard on the aggregate root. See [Section 6](#6-concurrency-handling-the-full-picture) for the full explanation.

**Why store `status` as VARCHAR and not a PostgreSQL ENUM?**

PostgreSQL ENUMs are hard to modify (you can ADD values but never REMOVE or RENAME them without recreating the type). VARCHAR with an application-level enum gives us flexibility to add states without DDL.

### 2.6 `user_tier_progress`

**What it stores**: Tracks a user's progress metrics for tier eligibility evaluation.

| Column | Type | Why |
|--------|------|-----|
| `id` | `UUID` | Primary key |
| `user_id` | `UUID` | Which user |
| `subscription_id` | `UUID FK` | Which subscription this progress relates to |
| `order_count` | `INT` | Orders placed in the evaluation period |
| `total_order_value_cents` | `BIGINT` | Total order value in cents |
| `period_start` | `TIMESTAMP WITH TIME ZONE` | Start of evaluation window |
| `period_end` | `TIMESTAMP WITH TIME ZONE` | End of evaluation window |
| `evaluated_at` | `TIMESTAMP WITH TIME ZONE` | When this was last computed |

**Why a separate table instead of fields on `user_subscriptions`?**

Separation of concerns. `UserSubscription` is the subscription aggregate. `UserTierProgress` is analytics/metrics data that changes at a different rate (updated on every order, evaluated daily by a scheduler). Keeping them separate:
- Avoids version bumps on the subscription for every order placement.
- Allows independent scaling of the progress tracking logic.
- Keeps the subscription aggregate focused on its core responsibility.

**Why `evaluated_at`?**

Prevents re-evaluation. The `TierReevaluationJob` checks this timestamp to skip users already evaluated recently. Guards against scheduler double-runs.

### 2.7 `payment_transactions`

**What it stores**: Every payment attempt (successful, failed, refunded).

| Column | Type | Why |
|--------|------|-----|
| `id` | `UUID` | Primary key |
| `subscription_id` | `UUID FK` | Which subscription this payment is for |
| `user_id` | `UUID` | Denormalized for query performance |
| `amount_cents` | `BIGINT` | Amount charged |
| `currency` | `VARCHAR(3)` | ISO 4217 |
| `status` | `VARCHAR(20)` | `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED` |
| `external_txn_id` | `VARCHAR(255)` | Transaction ID from the payment gateway |
| `idempotency_key` | `VARCHAR(255) UNIQUE` | Prevents duplicate charges |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | When the payment was initiated |

**Why denormalize `user_id`?**

In a production system, you'd frequently query "show me all payments for user X" (support tools, user dashboard). Without `user_id` here, every such query requires a JOIN through `user_subscriptions`. The denormalization is deliberate for read performance.

**Why `idempotency_key` with a UNIQUE constraint?**

This is the database-level guard against double-charging. If a payment request is retried (network timeout, client retry), the same `idempotency_key` is sent. The payment gateway uses it to return the same result. Our UNIQUE constraint ensures we never record two different transactions with the same key.

**Why `external_txn_id`?**

Reconciliation. When you dispute a charge or debug a payment issue, you need the payment gateway's reference ID to look it up in their dashboard.

### 2.8 `idempotency_records`

**What it stores**: Cached responses for idempotent API operations.

| Column | Type | Why |
|--------|------|-----|
| `id` | `UUID` | Primary key |
| `idempotency_key` | `VARCHAR(255) UNIQUE` | The client-provided dedup key |
| `resource_type` | `VARCHAR(50)` | What resource was created (e.g., `SUBSCRIPTION`) |
| `resource_id` | `UUID` | ID of the created resource |
| `response_payload` | `JSONB` | The full HTTP response body (serialized) |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | When the original request was processed |
| `expires_at` | `TIMESTAMP WITH TIME ZONE` | When this record can be cleaned up |

**Why a separate `idempotency_records` table instead of checking the resource directly?**

Three reasons:
1. **Response fidelity**: The second request should return the *exact same response* as the first (same HTTP status, same body). Storing the response payload guarantees this.
2. **Cross-resource idempotency**: What if a single API call creates multiple resources? The idempotency record is the single source of truth.
3. **Timing**: Between "payment succeeded" and "subscription saved", if a retry arrives, we need to know the first request is in-flight. The idempotency record acts as a lock.

**Why `expires_at`?**

Idempotency records don't need to live forever. After 24-48 hours, it's safe to assume the client won't retry. Expiry allows cleanup and prevents unbounded table growth.

---

## 3. Why PostgreSQL Over MySQL

**The deciding factor: Partial Unique Indexes.**

The single most important data integrity constraint in this system is: **a user can have at most one active subscription at any time**. In PostgreSQL:

```sql
CREATE UNIQUE INDEX idx_one_active_subscription_per_user
ON user_subscriptions (user_id)
WHERE status IN ('ACTIVE', 'GRACE_PERIOD', 'PENDING_PAYMENT', 'UPGRADE_PENDING', 'DOWNGRADE_SCHEDULED');
```

This creates a unique constraint that *only applies to rows matching the WHERE clause*. A user can have multiple `EXPIRED` or `CANCELLED` subscriptions (history), but only one in any "active-like" state.

**MySQL cannot do this.** In MySQL, you'd need to:
- Add a generated column like `active_flag` that's `user_id` when active and `NULL` when inactive, then put a unique index on it (hacky, fragile).
- Or maintain a separate `active_subscriptions` tracking table (more complexity, more failure modes).
- Or rely solely on application-level checks (weaker guarantee under concurrency).

**Secondary factor: JSONB.**

PostgreSQL's `JSONB` type supports GIN indexing and rich query operators (`@>`, `?`, `->>`). MySQL 8+ has `JSON` but it's stored as text internally, has no GIN indexing, and querying is significantly slower for complex operations.

**What we'd lose by choosing MySQL:**
- Database-enforced uniqueness on active subscriptions (replaced by weaker application checks)
- Efficient JSON indexing (matters if we ever need to query by benefit config)
- `SELECT ... FOR UPDATE SKIP LOCKED` works in both, so scheduler design is unaffected

---

## 4. Why JSONB Instead of Separate Tables

### The Alternative: Fully Normalized Schema

```
tier_benefits -> benefit_discount_configs (percent, categories)
             -> benefit_delivery_configs (max_deliveries, min_order)
             -> benefit_access_configs (hours_before_sale)
             -> benefit_lounge_configs (city, lounge_id)
             -> ... (new table for every benefit type)
```

### Why We Rejected Full Normalization

1. **Every new benefit type requires a schema migration.** In production, DDL changes are risky (table locks, deployment coordination). JSONB means new benefit types are purely a code + data change.

2. **The JOIN explosion.** To load a tier's benefits, you'd need LEFT JOINs across every benefit config table. With JSONB, it's a single query on `tier_benefits`.

3. **Sparse columns.** If you put all config in one wide table, most columns are NULL for any given row. JSONB avoids this naturally.

4. **The config is read-only at query time.** We never query *inside* the JSON to find "all tiers with discount > 10%". We load the tier's benefits, then the evaluator interprets the config. This means we don't need relational queryability on the config internals.

### When Would Full Normalization Be Better?

If we needed to run analytical queries across all benefit configs (e.g., "find all tiers offering > 15% discount"), JSONB queries would be slower than indexed columns. In that case, a hybrid approach works: JSONB for the engine, plus a materialized view or denormalized analytics table for reporting.

---

## 5. Subscription Lifecycle State Machine

### Why a State Machine?

Without a state machine, subscription mutations are validated with scattered `if/else` checks across service methods. The state machine:
- **Makes valid transitions explicit** (a map, not prose in a wiki).
- **Makes invalid transitions fail loudly** (exception, not silent corruption).
- **Is independently testable** (pure function, no database, no Spring).

### The States

| State | Meaning | How We Get Here |
|-------|---------|----------------|
| `PENDING_PAYMENT` | Subscription created, awaiting payment confirmation | User subscribes |
| `ACTIVE` | Fully active, benefits available | Payment succeeds |
| `UPGRADE_PENDING` | User requested upgrade, awaiting payment for the difference | Upgrade requested |
| `DOWNGRADE_SCHEDULED` | Downgrade requested, will apply at period end | Downgrade requested |
| `CANCELLED` | User cancelled, benefits available until `end_date` | User cancels |
| `GRACE_PERIOD` | Subscription expired, short window for renewal | `end_date` passed |
| `EXPIRED` | Fully expired, no benefits | Grace period ended without renewal |
| `PAYMENT_FAILED` | Initial payment failed | Payment gateway returns failure |

### The Transition Map

```
PENDING_PAYMENT    -> ACTIVE, PAYMENT_FAILED
PAYMENT_FAILED     -> PENDING_PAYMENT (retry)
ACTIVE             -> UPGRADE_PENDING, DOWNGRADE_SCHEDULED, CANCELLED, GRACE_PERIOD
UPGRADE_PENDING    -> ACTIVE (success or rollback)
DOWNGRADE_SCHEDULED -> ACTIVE (downgrade applied at period end)
GRACE_PERIOD       -> ACTIVE (renewed), EXPIRED
CANCELLED          -> (terminal)
EXPIRED            -> (terminal)
```

### Why Are Downgrades Deferred?

**Abuse scenario**: User buys Platinum (all benefits). Uses free delivery and 15% discounts all month. On day 29, downgrades to Silver and requests a prorated refund for the price difference. Effectively got Platinum benefits for Silver price.

**Our design**: Downgrade is *scheduled*. User keeps current tier until the billing period ends. Next period starts at the lower tier. No refund needed because user paid for what they used.

### Why Is Cancellation Not Immediate?

Similar reasoning. Cancelled users keep benefits until `end_date`. This is standard industry practice (Netflix, Spotify, etc.) and avoids refund complexity.

### Why `GRACE_PERIOD` Instead of Direct `EXPIRED`?

Real-world UX. A user's payment method might have expired. Instead of immediately revoking benefits:
1. Move to `GRACE_PERIOD` (configurable, e.g., 3 days).
2. Send renewal reminders.
3. If auto-renew is on, retry payment.
4. Only after grace period ends, move to `EXPIRED`.

This reduces involuntary churn. Industry data suggests 20-30% of expired subscriptions recover during grace periods.

---

## 6. Concurrency Handling: The Full Picture

This is the section most likely to impress an interviewer. We use **four layers of concurrency defense**, each catching what the previous layer misses.

### Layer 1: Application-Level Check (Weakest)

```java
Optional<UserSubscription> existing = repo.findActiveByUserId(userId);
if (existing.isPresent()) {
    throw new SubscriptionConflictException("User already has an active subscription");
}
```

**What it catches**: The obvious case. User already has a subscription, new request comes in, we check and reject.

**What it misses**: Two requests arrive simultaneously. Both read "no active subscription" at the same time. Both proceed to create one. This is a classic **TOCTOU (Time Of Check, Time Of Use)** race condition.

### Layer 2: Partial Unique Index (Strongest for Duplicate Prevention)

```sql
CREATE UNIQUE INDEX idx_one_active_subscription_per_user
ON user_subscriptions (user_id)
WHERE status IN ('ACTIVE', 'GRACE_PERIOD', 'PENDING_PAYMENT', 'UPGRADE_PENDING', 'DOWNGRADE_SCHEDULED');
```

**What it catches**: The TOCTOU race. Even if two transactions both pass the application check, only one can INSERT a row that satisfies the index condition. The second gets a unique constraint violation, which we catch and translate to `409 Conflict`.

**Why this is better than `SELECT ... FOR UPDATE`**: `FOR UPDATE` requires an existing row to lock. For the "first subscription" case, there's no row yet. The partial unique index works even for INSERTs.

### Layer 3: Optimistic Locking (For Updates to Existing Subscriptions)

The `version` column on `user_subscriptions`. JPA's `@Version` annotation.

```
Thread A: reads subscription (version=3)
Thread B: reads subscription (version=3)
Thread A: updates status to CANCELLED, sets version=4 -> SUCCESS
Thread B: updates status to UPGRADE_PENDING, sets version=4 -> FAILS (expected version=3, actual=4)
```

**What it catches**: Concurrent upgrades, concurrent upgrade + cancel, concurrent cancel + expire.

**Why optimistic over pessimistic?**

- **Pessimistic locking** (`SELECT ... FOR UPDATE`) holds a row lock for the entire transaction duration. If the transaction includes an external call (payment gateway), the lock is held for seconds, blocking all other requests for that user.
- **Optimistic locking** doesn't hold any lock during the transaction. It only checks at commit time. 99% of the time, there's no contention, so no overhead. The 1% that conflicts gets a clean retry or error.
- For our workload (low contention per user, external calls in the transaction), optimistic locking is the right choice.

**Interview follow-up: "When would you use pessimistic locking instead?"**

When contention is *high* and you want to avoid retry storms. Example: a single counter being incremented by thousands of concurrent requests. Optimistic locking would fail most attempts, wasting work. Pessimistic locking serializes access, which is actually more efficient when contention is > ~30%.

### Layer 4: Idempotency Records (For Retries)

See [Section 7](#7-idempotency-why-where-and-how).

### Concurrency Matrix: What Protects What

| Scenario | Protection Layer |
|----------|-----------------|
| Two subscribe requests for same user | Partial unique index |
| Upgrade + Cancel arrive simultaneously | Optimistic locking |
| Client retries a subscribe request | Idempotency record |
| Scheduler processes same expiry twice | `SELECT ... FOR UPDATE SKIP LOCKED` + optimistic lock |
| Two payment webhooks for same transaction | Idempotency key on `payment_transactions` |
| Duplicate payment gateway callback | `external_txn_id` uniqueness + idempotency |

---

## 7. Idempotency: Why, Where, and How

### Why Idempotency Matters

In distributed systems, exactly-once delivery is impossible (proven by the Two Generals Problem). So we design for **at-least-once delivery with idempotent processing** — the same request processed multiple times produces the same result as processing it once.

**Real scenarios where duplicates happen:**
- Client sends POST, server processes it, response is lost due to network timeout. Client retries.
- Load balancer retries a request that timed out at the application level but actually succeeded.
- Payment gateway sends a webhook, our server responds 200, but the gateway doesn't receive it and retries.
- User rage-clicks the "Subscribe" button.

### How It Works

Every mutation endpoint requires an `idempotencyKey` (UUID generated by the client).

**Flow**:

```
Request arrives with idempotencyKey = "abc-123"
    |
    v
Check idempotency_records for "abc-123"
    |
    +-- Found + not expired --> Return cached response_payload (same status code, same body)
    |
    +-- Not found --> Process the request
                          |
                          v
                    Execute business logic
                          |
                          v
                    Save result + idempotency record in SAME transaction
                          |
                          +-- Success --> Return response, cache in idempotency_records
                          |
                          +-- Unique constraint violation on idempotency_key
                              (concurrent duplicate) --> Read and return the cached response
```

### Why Store the Full Response Payload?

The second request must return **exactly** what the first returned — same HTTP status, same response body. If we only stored the resource ID, we'd need to reconstruct the response, which might differ if the resource was modified between the first and second request.

### Why `expires_at`?

Idempotency records are not audit logs. They're caches. Keeping them forever:
- Wastes storage (every API call creates a record).
- Slows down uniqueness checks as the table grows.
- Doesn't match the use case (retries happen within seconds/minutes, not months).

We expire them after 24 hours. A cleanup job periodically deletes expired records.

### Idempotency vs. Optimistic Locking — What's the Difference?

| | Idempotency | Optimistic Locking |
|---|---|---|
| **Protects against** | Duplicate requests (same intent) | Concurrent requests (different intents) |
| **Example** | User clicks Subscribe twice | User clicks Subscribe while admin cancels |
| **Mechanism** | Cache the response, return it on retry | Version check on commit |
| **Response on conflict** | `200` (same response) | `409 Conflict` |
| **Requires client cooperation** | Yes (must send idempotency key) | No |

---

## 8. Transaction Boundaries: What Goes Where

### The Golden Rule

> **One transaction = one use case = one command handler method.**

The `@Transactional` annotation lives on the `SubscriptionCommandHandler` methods, NOT on domain services or repositories.

**Why?**

- **Controllers** shouldn't manage transactions (they're about HTTP, not business logic).
- **Domain services** shouldn't manage transactions (they're about business rules, not infrastructure).
- **Repositories** shouldn't have broader transactions (each repo method is too granular).
- **Command handlers** orchestrate a use case: validate, call domain service, save, publish event. This is the natural transaction boundary.

### The Payment Problem

A subscription flow calls an external payment gateway. Should this be inside or outside the transaction?

**Option A: Payment inside the transaction**
```
BEGIN TRANSACTION
  check idempotency
  validate eligibility
  call payment gateway  <-- holds DB connection open for 2-5 seconds
  save subscription
  save payment record
COMMIT
```
Problem: The database connection is held open during the entire external call. Under load, you exhaust your connection pool.

**Option B: Payment outside the transaction (our approach)**
```
call payment gateway (with idempotency key)  <-- no DB connection held
    |
    v
BEGIN TRANSACTION
  check idempotency
  validate eligibility
  save subscription (with payment reference)
  save payment record
  save idempotency record
COMMIT
```
Problem: What if payment succeeds but DB commit fails?

**Solution**: The payment was made with an `idempotency_key`. When we retry the entire flow:
1. Payment gateway receives the same idempotency key, returns the same successful result (no double charge).
2. Our idempotency check hasn't recorded a result yet (the commit failed), so we proceed.
3. This time the DB commit succeeds.

**This is why idempotency on both sides (our API + payment gateway) is critical.** It makes the non-atomic operation (external call + DB write) safely retriable.

### What Gets Published Inside vs. Outside the Transaction?

**Inside** (via `ApplicationEventPublisher.publishEvent()`): The event is published during the transaction. `@TransactionalEventListener(phase = AFTER_COMMIT)` ensures listeners only fire after successful commit. If the transaction rolls back, no event is delivered.

**Why not publish after the commit in application code?** If the application crashes between commit and publish, the event is lost. `@TransactionalEventListener(AFTER_COMMIT)` is tied to the transaction lifecycle, so it fires reliably as long as the JVM is alive at commit time.

**What about true at-least-once delivery?** For critical events (e.g., triggering notifications), a future improvement would be the **Transactional Outbox Pattern**: write the event to an `outbox` table inside the same transaction, then a separate poller/CDC reads and publishes it. This guarantees delivery even across JVM crashes.

---

## 9. Rule Engine: Eligibility Evaluation

### The Problem

To subscribe to Gold tier, a user might need:
- 10+ orders in the last 30 days
- Rs 5000+ total spend in the last 30 days
- Be in the "premium" or "early_adopter" cohort

Tomorrow, business adds:
- Account age > 6 months
- No order cancellation rate > 20%

We need to support arbitrary, configurable, combinable rules without changing the evaluation logic.

### The Solution: Strategy + Composite Pattern

```
EligibilityRule (interface)
    |
    +-- OrderCountRule          : checks orderCount >= config.minOrders
    +-- OrderValueRule          : checks totalValueCents >= config.minValueCents
    +-- CohortRule              : checks userCohort IN config.allowedCohorts
    +-- (future) AccountAgeRule : checks accountAge >= config.minDays
    +-- (future) CancelRateRule : checks cancelRate <= config.maxRate

CompositeEligibilityEvaluator
    |
    +-- holds List<EligibilityRule> (auto-injected by Spring)
    +-- loads TierEligibilityRule rows from DB for the target tier
    +-- matches each DB rule to a strategy by ruleType
    +-- evaluates and combines with AND/OR logic
```

### How Adding a New Rule Works

**Step 1**: Create `AccountAgeRule.java` implementing `EligibilityRule`, annotate with `@Component`. This class knows how to read `{"minDays": 180}` from config_json and check it against the user's account age.

**Step 2**: Insert a row in `tier_eligibility_rules`:
```sql
INSERT INTO tier_eligibility_rules (tier_id, rule_type, config_json, operator)
VALUES ('gold-tier-uuid', 'ACCOUNT_AGE', '{"minDays": 180}', 'AND');
```

**Step 3**: There is no step 3. The `CompositeEligibilityEvaluator` discovers the new `@Component` via Spring's dependency injection, finds the matching `rule_type` at runtime, and evaluates it.

### Why Not a Generic Expression Language (SpEL, MVEL)?

An expression engine like `"orders > 10 && totalValue > 5000"` stored as a string is more flexible but:
- **Security risk**: arbitrary code execution from database values.
- **Debugging nightmare**: no IDE support, no type checking, stack traces are opaque.
- **Performance**: expression parsing/compilation on every evaluation.
- **Testing**: hard to unit test an expression stored in a DB string.

Strategy pattern gives us the same extensibility with type safety, testability, and debuggability.

---

## 10. Benefit Engine: How Discounts Actually Work

### The Problem

When a user with a Gold membership places an order at checkout, the system needs to:
1. Check if the user has an active membership.
2. Determine what benefits apply to this specific order.
3. Calculate the discount amount.
4. Check free delivery eligibility.
5. Return a summary for the checkout service to apply.

### The Architecture

Same Strategy + Composite pattern as the rule engine:

```
BenefitEvaluator (interface)
    |
    +-- FreeDeliveryEvaluator
    |       reads: {"maxFreeDeliveries": 5, "minOrderValueCents": 20000}
    |       checks: user hasn't exceeded monthly limit, order meets minimum
    |       returns: freeDelivery=true/false
    |
    +-- DiscountEvaluator
    |       reads: {"discountPercent": 10, "applicableCategories": ["dairy", "snacks"], "maxDiscountCents": 10000}
    |       checks: order contains items in applicable categories
    |       returns: discountCents = min(orderValue * percent / 100, maxDiscountCents)
    |
    +-- (future) ExclusiveDealEvaluator
    +-- (future) EarlyAccessEvaluator
    +-- (future) PrioritySupportEvaluator

CompositeBenefitEvaluator
    |
    +-- loads TierBenefit rows for user's current tier
    +-- delegates to matching BenefitEvaluator by benefitType
    +-- aggregates results into a single BenefitValidationResponse
```

### Why Benefits Are Not Hardcoded

**Bad approach**:
```java
if (tier.equals("Gold")) {
    discount = orderValue * 0.10;
    freeDelivery = true;
}
```

**Why it's bad**:
- Changing Gold's discount from 10% to 12% requires a code change + deployment.
- Adding a cap on discount requires a code change.
- Adding "lounge access" benefit requires a code change to the if/else chain.

**Our approach**: The 10% and the delivery eligibility are **data** (in `config_json`), not code. Changing them is a database update. Adding a new benefit type is a new evaluator class + database rows.

### The Benefit Validation API Flow

```
Checkout service calls: POST /api/v1/benefits/validate
    |
    v
BenefitController receives BenefitValidationRequest
    |
    v
BenefitValidationUseCase.validate(request)
    |
    v
1. Load user's active subscription (cache-first, DB-fallback)
2. If no active subscription -> return {eligible: false}
3. If subscription expired since last cache refresh -> return {eligible: false}
4. Load tier's benefits (cached)
5. Build BenefitContext from request (orderId, orderValue, categories)
6. CompositeBenefitEvaluator.evaluate(context) -> aggregated BenefitResult
7. Return BenefitValidationResponse
```

### Why Check Expiry at Benefit Validation Time?

Edge case: User's subscription expires at 3:00 PM. At 2:59 PM, user adds items to cart (subscription is active). At 3:01 PM, user checks out (subscription is now expired). The benefit validation call at 3:01 PM must return `eligible: false`.

We always check the subscription's `end_date` against the current time, not just the cached `status`.

---

## 11. Event-Driven Design

### Why Events?

When a subscription is created, multiple things need to happen:
- Send a welcome email
- Update analytics
- Trigger a CRM workflow
- Log an audit trail

**Without events**: The `SubscriptionCommandHandler.subscribe()` method calls `emailService.sendWelcome()`, `analyticsService.track()`, `crmService.trigger()`, etc. The subscription handler now depends on 5+ services and any failure in email sending could fail the subscription.

**With events**: The handler publishes `SubscriptionCreatedEvent`. Independent listeners handle side effects. The subscription handler doesn't know or care about email, analytics, or CRM.

### Event Types and Their Purposes

| Event | When | Typical Listeners |
|-------|------|-------------------|
| `SubscriptionCreatedEvent` | After successful subscription | Welcome email, analytics, audit log |
| `SubscriptionCancelledEvent` | After cancellation | Retention offer, analytics, audit log |
| `SubscriptionExpiredEvent` | After expiry (scheduler) | Win-back email, analytics |
| `TierUpgradedEvent` | After upgrade confirmed | Congratulations email, analytics |
| `TierDowngradedEvent` | After downgrade applied | Feedback survey, analytics |
| `PaymentSucceededEvent` | After payment success | Receipt email, analytics |
| `PaymentFailedEvent` | After payment failure | Retry notification, alert |

### Phase 1 vs. Phase 2 Event Infrastructure

**Phase 1 (current)**: Spring's `ApplicationEventPublisher` + `@TransactionalEventListener`.
- Events are in-process only (same JVM).
- `AFTER_COMMIT` phase ensures listeners only fire on successful transactions.
- Synchronous by default (but can be made async with `@Async`).

**Phase 2 (future)**: Kafka.
- The `EventPublisher` port interface stays the same.
- Replace `SpringEventPublisher` adapter with `KafkaEventPublisher`.
- Events now cross service boundaries.
- Need to handle: ordering, partitioning (by userId), at-least-once delivery, consumer idempotency.

**Why design for Kafka now even though we don't use it?**
- The event model (event ID, timestamp, event type, payload) is already Kafka-compatible.
- The port/adapter boundary means the swap is an infrastructure change, not a domain change.
- Interviewers specifically look for this foresight.

---

## 12. Scheduler Design: Background Jobs

### Why Schedulers?

Some operations can't be triggered by user requests:
- Subscription expiry (happens when `end_date` passes, not when user acts)
- Auto-renewal (triggered by time, not user action)
- Deferred downgrade application (scheduled for period end)
- Tier re-evaluation (periodic based on order history)

### The Scheduler Concurrency Problem

**Problem**: In a multi-instance deployment (2+ app servers), `@Scheduled` runs on every instance. Without coordination:
- Instance A and Instance B both run the expiry job.
- Both query the same expired subscriptions.
- Both try to transition them to `GRACE_PERIOD`.
- One succeeds, one fails with optimistic lock exception.
- Wasted work, error noise in logs.

**Solution: ShedLock** (distributed locking using PostgreSQL).

ShedLock creates a `shedlock` table in PostgreSQL. Before a scheduled job runs, it attempts to acquire a row-level lock. Only one instance succeeds. The lock has a TTL (`lockAtMostFor`) to prevent deadlocks if the instance crashes.

```java
@Scheduled(fixedDelay = 300_000)  // every 5 minutes
@SchedulerLock(name = "expiryJob", lockAtLeastFor = "PT4M", lockAtMostFor = "PT10M")
public void processExpiredSubscriptions() {
    // Only one instance runs this
}
```

### `SELECT ... FOR UPDATE SKIP LOCKED`

Even with ShedLock (single-instance execution), we use `FOR UPDATE SKIP LOCKED` for:
1. **Future-proofing**: If we later want parallel processing (multiple threads within one instance).
2. **Safety against overlapping runs**: If a job takes longer than expected and the next scheduled run starts before it finishes, `SKIP LOCKED` ensures the new run processes different rows.

```sql
SELECT * FROM user_subscriptions
WHERE status = 'ACTIVE' AND end_date < NOW()
ORDER BY end_date ASC
LIMIT 100
FOR UPDATE SKIP LOCKED;
```

`SKIP LOCKED` means: "If a row is already locked by another transaction, skip it instead of waiting." This is non-blocking and prevents contention.

### Batch Processing

Jobs process in batches (e.g., 100 rows per iteration) rather than loading all eligible rows at once:
- **Memory safety**: Don't load 10,000 rows into memory.
- **Transaction brevity**: Shorter transactions mean shorter lock durations.
- **Progress visibility**: Each batch is a commit point. If the job crashes mid-way, already-processed batches are saved.

---

## 13. Caching Strategy

### What We Cache and Why

| Data | Cache | TTL | Invalidation | Why |
|------|-------|-----|-------------|-----|
| Plans + Tiers + Benefits | Redis (`127.0.0.1:6379`) | 5 min | On admin update (`@CacheEvict`) | Read thousands of times per second at checkout. Changes monthly at most |
| Eligibility Rules | Redis | 5 min | On admin update | Same reasoning as above |
| Active Subscription (by userId) | Redis | 30 sec | On any subscription mutation | Called on every checkout. Short TTL because subscription state changes more often |

### Why Redis?

- **Shared state**: If we scale to multiple app instances behind a load balancer, all instances see the same cache. With in-process caches (Caffeine), Instance A evicts on a subscription update but Instance B still serves stale data.
- **Survives restarts**: App restarts don't cold-start the cache. Redis retains data across deployments.
- **Atomic operations**: Redis `SETNX`, `INCR`, etc. can serve as lightweight distributed locks if needed beyond ShedLock.
- **Observability**: Redis has built-in monitoring (`INFO`, `MONITOR`, `SLOWLOG`) to diagnose cache hit rates and latency.

**Trade-off vs. Caffeine**: Redis adds ~1ms network round-trip per cache call (vs. ~100ns for in-process Caffeine). For our workload (benefit validation during checkout), 1ms is negligible compared to the ~5-50ms saved by not hitting PostgreSQL. The consistency and scalability benefits far outweigh the latency cost.

### How Spring Data Redis Integration Works

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
  cache:
    type: redis
    redis:
      time-to-live: 300000  # default 5 min TTL
```

We use `@Cacheable` / `@CacheEvict` annotations on the application layer, with a `RedisConfig` class that configures `RedisCacheManager` with per-cache TTLs (5 min for catalog, 30s for active subscriptions).

### Cache Stampede Prevention

When a cache entry expires, and 100 concurrent requests arrive for the same key, all 100 would hit the database simultaneously. We mitigate this with Redis's `SETNX`-based locking: only one thread fetches from DB and writes to cache, others wait briefly and retry the cache read. Spring's `@Cacheable(sync = true)` enables this behavior.

---

## 14. Edge Cases and How We Handle Them

### EC-1: User Buys the Same Plan Twice

**Scenario**: User has an active Monthly Silver subscription. User sends another POST /subscriptions for Monthly Silver.

**Protection**:
1. Application check: `findActiveByUserId()` returns existing subscription -> `409 Conflict`.
2. Even if application check races, partial unique index rejects the INSERT.

### EC-2: Upgrade During Active Subscription

**Scenario**: User has Monthly Silver, wants Monthly Gold.

**Flow**:
1. Validate: `gold.rank (2) > silver.rank (1)` -> yes, it's an upgrade.
2. Check eligibility: Does user meet Gold's eligibility rules? If not -> `422`.
3. Calculate prorated cost: `(daysRemaining / totalDays) * (gold.price - silver.price)`.
4. Charge prorated amount via payment gateway.
5. Update subscription: `tier_id = gold.id`, `previous_tier_id = silver.id`, `status = ACTIVE`.
6. Publish `TierUpgradedEvent`.

### EC-3: Downgrade Abuse

**Scenario**: User buys Platinum, uses all benefits, downgrades on day 29 for a refund.

**Protection**: Downgrades are **scheduled**, not immediate.
- Status changes to `DOWNGRADE_SCHEDULED`.
- `scheduled_tier_id` is set to the lower tier.
- User keeps Platinum benefits until `end_date`.
- A scheduler job applies the downgrade when `end_date` arrives.
- No refund is issued (user got what they paid for).

### EC-4: Membership Expiry During Checkout

**Scenario**: User starts checkout at 2:59 PM (membership active). Completes checkout at 3:01 PM (membership expired at 3:00 PM).

**Protection**: `POST /benefits/validate` always checks `subscription.endDate` against `Instant.now()`. Even if the cached status is `ACTIVE`, the endpoint compares timestamps and returns `eligible: false` if `endDate` has passed.

### EC-5: Concurrent Payment Success + Cancellation

**Scenario**: User clicks "Cancel" while a renewal payment is being processed by the payment gateway.

**Flow**:
1. Cancel request: reads subscription (version=5), sets status to `CANCELLED`, writes (version=6). Succeeds.
2. Payment webhook arrives: reads subscription (version=6, status=CANCELLED). State machine check: `CANCELLED -> ACTIVE` is NOT a valid transition. Webhook handler rejects. Payment is refunded.

**Alternative flow** (payment wins first):
1. Payment webhook: reads subscription (version=5, status=GRACE_PERIOD), sets status to `ACTIVE`, writes (version=6). Succeeds.
2. Cancel request: reads subscription (version=6, status=ACTIVE). `ACTIVE -> CANCELLED` is valid. Proceeds. User is cancelled.

In both cases, the final state is consistent. The optimistic lock ensures only one writer succeeds per version.

### EC-6: Tier Recalculation During Active Checkout

**Scenario**: User places an order (order count increases). Tier reevaluation job runs. User now qualifies for Gold (was Silver). Meanwhile, the checkout benefit validation is using the Silver tier's benefits.

**Protection**: Tier upgrades are NOT automatic. The `TierReevaluationJob` only publishes a notification event ("you now qualify for Gold"). The user must explicitly request the upgrade. This avoids mid-checkout tier changes.

### EC-7: Scheduler Running Twice

**Scenario**: Due to clock skew or deployment overlap, the expiry job runs on two instances simultaneously.

**Protection (3 layers)**:
1. **ShedLock**: Only one instance acquires the lock. Second instance skips.
2. **`FOR UPDATE SKIP LOCKED`**: Even without ShedLock, rows locked by the first instance are skipped by the second.
3. **Optimistic locking**: Even if both somehow read the same row, only the first UPDATE succeeds.

### EC-8: Duplicate Webhook Events

**Scenario**: Payment gateway sends the same webhook event twice (at-least-once delivery).

**Protection**: Webhook handler checks `payment_transactions.idempotency_key`. The second webhook with the same key finds the existing record and returns success without reprocessing.

### EC-9: Partial Failures in Multi-Step Operations

**Scenario**: During subscription creation, saving the subscription succeeds but saving the payment transaction fails.

**Protection**: Both saves are in the same `@Transactional` method. If any save fails, the entire transaction rolls back. The subscription is NOT created. The client can safely retry (idempotency key hasn't been recorded since the transaction rolled back).

---

## 15. Indexing Strategy: Why Each Index Exists

### Principle: Every Index Serves a Hot Query Path

We don't add indexes speculatively. Each one is justified by a specific query pattern.

### Index 1: Partial Unique Index on Active Subscriptions

```sql
CREATE UNIQUE INDEX idx_one_active_sub_per_user
ON user_subscriptions (user_id)
WHERE status IN ('ACTIVE', 'GRACE_PERIOD', 'PENDING_PAYMENT', 'UPGRADE_PENDING', 'DOWNGRADE_SCHEDULED');
```

**Query it serves**: The uniqueness constraint itself (not a query, a write-time invariant).
**Why partial**: Users can have many `EXPIRED`/`CANCELLED` rows. We only restrict "active-like" states.
**Performance impact**: Tiny index (only active rows), fast to maintain.

### Index 2: Status + End Date (Expiry Scheduler)

```sql
CREATE INDEX idx_sub_status_end_date ON user_subscriptions (status, end_date);
```

**Query it serves**:
```sql
SELECT * FROM user_subscriptions WHERE status = 'ACTIVE' AND end_date < NOW() LIMIT 100;
```
**Why**: The expiry scheduler runs every 5 minutes. Without this index, it scans the entire table. With it, it does an index range scan on (status='ACTIVE', end_date < NOW()).

### Index 3: User ID + Status (Active Subscription Lookup)

```sql
CREATE INDEX idx_sub_user_status ON user_subscriptions (user_id, status);
```

**Query it serves**:
```sql
SELECT * FROM user_subscriptions WHERE user_id = ? AND status = 'ACTIVE';
```
**Why**: Called on every benefit validation (checkout), every API call to "get my subscription". This is the hottest read path.

### Index 4: Idempotency Key on Payment Transactions

```sql
CREATE UNIQUE INDEX idx_payment_idempotency ON payment_transactions (idempotency_key);
```

**Query it serves**: Duplicate payment detection.
**Why UNIQUE**: Ensures at the database level that we never record two payments with the same idempotency key.

### Index 5: Idempotency Key on Idempotency Records

```sql
CREATE UNIQUE INDEX idx_idempotency_key ON idempotency_records (idempotency_key);
```

**Query it serves**: The first step of every mutation request (check if already processed).
**Why UNIQUE**: Same as above, database-level dedup.

### Index 6: Tier Benefits Lookup

```sql
CREATE INDEX idx_tier_benefits ON tier_benefits (tier_id, active);
```

**Query it serves**: Loading benefits for a tier during benefit validation.
**Why composite**: We always query `WHERE tier_id = ? AND active = true`.

### What About Indexes We Didn't Add?

- **No index on `created_at`**: We don't query by creation time in hot paths. Only useful for admin queries, which can tolerate slower performance.
- **No index on `user_id` alone on `user_subscriptions`**: The composite `(user_id, status)` index covers queries that filter by user_id alone (leftmost prefix rule).

---

## 16. Error Handling Strategy

### Exception Hierarchy

```
DomainException (abstract base)
    |
    +-- SubscriptionConflictException    (409 Conflict)
    |       "User already has an active subscription"
    |
    +-- EligibilityNotMetException       (422 Unprocessable Entity)
    |       "User does not meet minimum order count (10) for Gold tier"
    |
    +-- IllegalStateTransitionException  (409 Conflict)
    |       "Cannot transition from CANCELLED to ACTIVE"
    |
    +-- SubscriptionNotFoundException    (404 Not Found)
    |       "No active subscription found for user"
    |
    +-- PaymentFailedException           (402 Payment Required)
    |       "Payment declined by gateway"
    |
    +-- IdempotencyViolationException    (409 Conflict)
    |       "Request with this idempotency key is already being processed"

Framework Exceptions (caught by GlobalExceptionHandler):
    +-- OptimisticLockException          (409 Conflict)
    |       "Resource was modified by another request"
    |
    +-- ConstraintViolationException     (409 Conflict)
    |       "Database constraint violated" (e.g., partial unique index)
    |
    +-- MethodArgumentNotValidException  (400 Bad Request)
            "Validation failed: userId must not be null"
```

### Why RFC 7807 Problem Detail?

Standard HTTP error responses vary across APIs. RFC 7807 defines a structured format:

```json
{
    "type": "https://firstclub.com/errors/eligibility-not-met",
    "title": "Eligibility Not Met",
    "status": 422,
    "detail": "User does not meet minimum order count (10) for Gold tier. Current count: 7.",
    "instance": "/api/v1/subscriptions",
    "timestamp": "2026-05-23T10:00:00Z",
    "traceId": "abc-123-def"
}
```

Benefits:
- `type` is a stable URI that client code can switch on (not a brittle string comparison on `detail`).
- `detail` is human-readable for debugging.
- `traceId` lets support teams correlate errors with server logs.
- Spring Boot 3 has built-in support for Problem Detail via `ProblemDetail` class.

### Why Specific HTTP Status Codes?

| Code | Meaning | When We Use It |
|------|---------|---------------|
| 400 | Bad Request | Malformed input (missing fields, invalid UUID format) |
| 402 | Payment Required | Payment failed or declined |
| 404 | Not Found | Subscription/plan/tier not found |
| 409 | Conflict | Duplicate subscription, concurrent modification, invalid state transition |
| 422 | Unprocessable Entity | Semantically valid but business-rule violated (eligibility not met) |
| 500 | Internal Server Error | Unexpected exceptions (bugs, infrastructure failures) |

**Why 409 for state machine violations?** The request is well-formed (not 400) and understood (not 422), but conflicts with the current state of the resource. This matches HTTP spec: "the request could not be completed due to a conflict with the current state of the target resource."

**Why 422 for eligibility failures?** The request is syntactically valid, but cannot be processed because a business rule is not satisfied. 422 (from WebDAV, widely adopted) conveys "I understand what you want, but I can't do it because of a semantic error."

---

## 17. API Design Decisions

### Why API Versioning (`/api/v1/`)?

If we change a response structure (e.g., rename `priceCents` to `priceInCents`), existing clients break. With versioning:
- `/api/v1/plans` returns the current format.
- `/api/v2/plans` returns the new format.
- Old clients continue working until migrated.

### Why POST for Cancel/Upgrade/Downgrade Instead of PUT/PATCH?

These are **commands that trigger side effects** (state transitions, payments, events), not simple data updates. POST conveys "this is an action" more clearly than PATCH (which implies "modify these fields").

REST purists might argue for `PATCH /subscriptions/{id}` with `{"status": "CANCELLED"}`, but this hides the business semantics. `POST /subscriptions/{id}/cancel` makes the intent explicit and allows a dedicated request/response DTO per action.

### Why `userId` as a Query Parameter and Not in the URL Path?

For `GET /subscriptions/me?userId={userId}`:
- In production, `userId` would come from the JWT token (not sent by the client at all).
- The `?userId=` parameter is a demo convenience.
- The `/me` endpoint name signals "this should be the authenticated user's subscription" — making the production upgrade path obvious.

### Why Require `idempotencyKey` in the Request Body?

Alternatives considered:
- **Header (`Idempotency-Key`)**: More RESTful, but easy to forget in Postman/curl testing. Also, headers are sometimes stripped by proxies.
- **Request body field**: Impossible to forget (it's required by validation). Always logged with the request payload. Survives proxy stripping.

We chose request body for pragmatism. In production, both approaches work.

---

## 18. Domain vs JPA Entity Separation

### Why Two Sets of Entities?

The domain layer has `domain/model/UserSubscription.java` (pure Java).
The infrastructure layer has `infrastructure/persistence/entity/UserSubscriptionEntity.java` (JPA-annotated).

**Why not just one?**

If we put JPA annotations on domain models:
- Domain depends on `jakarta.persistence` (framework coupling).
- Domain tests need JPA on the classpath.
- Domain models are shaped by JPA constraints (e.g., needing a no-arg constructor, mutable fields for JPA proxy).
- Hibernate lazy-loading proxies leak into domain logic.

With separation:
- Domain models are pure, immutable value objects with validation logic.
- JPA entities are dumb data carriers annotated for ORM.
- A mapper converts between them at the infrastructure boundary.

**Cost**: More code (mapper classes). **Benefit**: Domain layer is truly framework-independent, testable with plain JUnit, and portable.

**Interview follow-up: "Isn't this over-engineering?"**

For a small CRUD app, yes. For a domain with complex business rules (state machine, eligibility rules, benefit calculations), the separation pays for itself. The domain layer is where the most critical logic lives — it should be the easiest to test and the hardest to accidentally break with framework changes.

---

## 19. Testing Strategy

### Unit Tests (Domain Layer)

**What**: Pure Java tests. No Spring, no database, no mocking framework.

**Example — State Machine Test**:
```java
@Test
void shouldAllowTransitionFromActiveToUpgradePending() {
    SubscriptionStateMachine.validateTransition(ACTIVE, UPGRADE_PENDING);
    // no exception = pass
}

@Test
void shouldRejectTransitionFromCancelledToActive() {
    assertThrows(IllegalStateTransitionException.class,
        () -> SubscriptionStateMachine.validateTransition(CANCELLED, ACTIVE));
}
```

**Why these are valuable**: State machine bugs are subtle (allowing an invalid transition silently) and catastrophic (subscription in impossible state). Exhaustive transition testing catches them early.

**Example — Rule Engine Test**:
```java
@Test
void orderCountRuleShouldPassWhenCountExceedsMinimum() {
    var rule = new OrderCountRule();
    var config = objectMapper.readTree("{\"minOrders\": 10, \"periodDays\": 30}");
    var context = new EligibilityContext(userId, 15, 0, null, null);

    var result = rule.evaluate(context, config);

    assertTrue(result.isEligible());
}
```

### Integration Tests (Spring Boot + Testcontainers)

**What**: Full Spring context, real PostgreSQL (via Testcontainers), real HTTP calls.

**Example — Subscribe Flow**:
```java
@Test
void shouldCreateSubscriptionAndReturnIt() {
    // POST /api/v1/subscriptions
    var response = restTemplate.postForEntity("/api/v1/subscriptions", request, SubscriptionResponse.class);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNotNull(response.getBody().getId());
    assertEquals("ACTIVE", response.getBody().getStatus());
}
```

### Concurrency Tests

**What**: Multiple threads executing simultaneously to verify our concurrency guards work.

**Example — Duplicate Subscribe Prevention**:
```java
@Test
void onlyOneSubscriptionShouldSucceedUnderConcurrentRequests() {
    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger conflictCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            latch.await();  // all threads start simultaneously
            try {
                restTemplate.postForEntity("/api/v1/subscriptions", request, ...);
                successCount.incrementAndGet();
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.CONFLICT) {
                    conflictCount.incrementAndGet();
                }
            }
        });
    }
    latch.countDown();  // release all threads
    executor.awaitTermination(10, SECONDS);

    assertEquals(1, successCount.get());
    assertEquals(9, conflictCount.get());
}
```

**Why this test matters**: It proves that our partial unique index + optimistic locking actually works under real contention. A passing unit test of the application check alone wouldn't prove this.

---

## 20. What Would Change at Scale

### Current Design Limits

| Component | Current Limit | When It Breaks |
|-----------|--------------|----------------|
| In-memory cache (Caffeine) | Single instance | Multiple app instances have inconsistent caches |
| Spring Events | Same JVM only | Microservice extraction |
| ShedLock (PostgreSQL) | Single DB | Multi-region deployment |
| Benefit validation on DB read | ~5ms | 50,000+ concurrent checkouts |

### What Changes at Each Scale Point

**10,000 users (current design works fine)**:
- Single PostgreSQL instance handles the load.
- Redis cache is already in place.
- Single app instance serves all traffic.

**100,000 users (horizontal scaling)**:
- Multiple app instances behind a load balancer.
- Redis cache already shared across instances (no change needed).
- Read replicas for PostgreSQL (benefit validation reads from replica).

**1,000,000 users (event-driven architecture)**:
- Replace Spring Events with Kafka.
- Subscription writes and benefit validation reads are separated (CQRS).
- Benefit validation reads from a pre-computed Redis cache (updated by Kafka consumers on subscription changes).

**10,000,000+ users (full microservice extraction)**:
- Membership Engine becomes its own service.
- Dedicated PostgreSQL instance (or sharded by userId).
- Kafka topics for all inter-service communication.
- Rate limiting, circuit breakers, bulkheads on all external calls.
- Potential move to event sourcing for the subscription aggregate (full audit trail, temporal queries).

### The Key Architectural Advantage

Because we used hexagonal architecture with ports and adapters, every scaling step above is an **adapter swap**, not a rewrite:
- Single Redis -> Redis Cluster / Redis Sentinel for HA
- `SpringEventPublisher` -> `KafkaEventPublisher`
- `JpaSubscriptionRepository` -> `ShardedJpaSubscriptionRepository`

The domain layer — state machine, eligibility rules, benefit calculations — remains untouched at every scale point. That is the entire point of this architecture.
