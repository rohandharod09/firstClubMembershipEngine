# FirstClub Membership Engine — Complete Demo Guide

This guide walks through a **full membership lifecycle across 4 different users**, demonstrating all tiers, eligibility checks, upgrades, downgrades, cancellations, idempotency, conflict prevention, admin APIs, and checkout benefit application.

---

## Pre-requisites

Before the demo:
```bash
# 1. Ensure PostgreSQL is running
psql -U firstclub -d firstclub_membership -c "SELECT 1;"

# 2. Ensure Redis is running
redis-cli ping   # should return PONG

# 3. Start the application
./gradlew bootRun

# 4. Swagger UI (useful to show alongside curl)
open http://localhost:8080/swagger-ui.html
```

---

## Useful UUIDs (from seed data)

```bash
# Plans
MONTHLY_PLAN="a1b2c3d4-0001-0001-0001-000000000001"
QUARTERLY_PLAN="a1b2c3d4-0001-0001-0001-000000000002"
YEARLY_PLAN="a1b2c3d4-0001-0001-0001-000000000003"

# Monthly Tiers
MONTHLY_SILVER="b1b2c3d4-0001-0001-0001-000000000001"
MONTHLY_GOLD="b1b2c3d4-0001-0001-0001-000000000002"
MONTHLY_PLATINUM="b1b2c3d4-0001-0001-0001-000000000003"

# Users (make these up — any UUID works)
USER_A="aaaaaaaa-0000-0000-0000-000000000001"   # Regular user → Silver → upgrades to Gold
USER_B="bbbbbbbb-0000-0000-0000-000000000002"   # Power user → directly Gold
USER_C="cccccccc-0000-0000-0000-000000000003"   # Tries Platinum (fails eligibility)
USER_D="dddddddd-0000-0000-0000-000000000004"   # Subscribes then cancels
```

---

## SCENE 1: Browse the Catalog

### Step 1.1 — Get all plans and tiers

```bash
curl -s http://localhost:8080/api/v1/plans | jq
```

**What to show:** 3 plans (Monthly/Quarterly/Yearly), each with Silver/Gold/Platinum tiers, each tier showing its benefits and prices. Point out the `configJson` in benefits — this drives the benefit engine dynamically.

---

## SCENE 2: User A — Silver Subscription (Basic User)

### Step 2.1 — User A subscribes to Monthly Silver

Silver has NO eligibility requirements — anyone can subscribe.

```bash
curl -s -X POST http://localhost:8080/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "aaaaaaaa-0000-0000-0000-000000000001",
    "planId": "a1b2c3d4-0001-0001-0001-000000000001",
    "tierId": "b1b2c3d4-0001-0001-0001-000000000001",
    "autoRenew": true,
    "orderCount": 0,
    "totalOrderValueCents": 0,
    "idempotencyKey": "user-a-sub-001"
  }' | jq
```

**Expected:** `status: ACTIVE`  
**Copy the returned `id` as `SUB_A_ID` for next steps.**

### Step 2.2 — Fetch User A's active subscription

```bash
curl -s "http://localhost:8080/api/v1/subscriptions/me?userId=aaaaaaaa-0000-0000-0000-000000000001" | jq
```

### Step 2.3 — Validate benefits at checkout (Silver: 5% discount, limited free delivery)

```bash
curl -s -X POST http://localhost:8080/api/v1/benefits/validate \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "aaaaaaaa-0000-0000-0000-000000000001",
    "orderId": "11111111-0000-0000-0000-000000000001",
    "orderValueCents": 50000,
    "orderCategories": ["dairy", "snacks"],
    "deliveryRequested": true
  }' | jq
```

**Expected output:**
- `eligible: true`
- `freeDelivery: true` (order value ₹500 ≥ ₹199 minimum for Silver free delivery)
- `discountCents: 2500` (5% of ₹500)
- `tierName: "Silver"`

### Step 2.4 — Same request, delivery NOT requested

```bash
curl -s -X POST http://localhost:8080/api/v1/benefits/validate \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "aaaaaaaa-0000-0000-0000-000000000001",
    "orderId": "11111111-0000-0000-0000-000000000002",
    "orderValueCents": 50000,
    "orderCategories": ["dairy"],
    "deliveryRequested": false
  }' | jq
```

**Expected:** `freeDelivery: false`, discount still applies.

---

## SCENE 3: Idempotency — Same Request Twice

### Step 3.1 — Replay User A's subscription request with the SAME idempotency key

```bash
curl -s -X POST http://localhost:8080/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "aaaaaaaa-0000-0000-0000-000000000001",
    "planId": "a1b2c3d4-0001-0001-0001-000000000001",
    "tierId": "b1b2c3d4-0001-0001-0001-000000000001",
    "autoRenew": true,
    "orderCount": 0,
    "totalOrderValueCents": 0,
    "idempotencyKey": "user-a-sub-001"
  }' | jq
```

**Expected:** Returns the SAME subscription as before — not a new one. Same `id`. Status `201`. This is idempotency at work — safe to retry.

### Step 3.2 — Try subscribing AGAIN with a DIFFERENT idempotency key (conflict!)

```bash
curl -s -X POST http://localhost:8080/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "aaaaaaaa-0000-0000-0000-000000000001",
    "planId": "a1b2c3d4-0001-0001-0001-000000000001",
    "tierId": "b1b2c3d4-0001-0001-0001-000000000001",
    "autoRenew": true,
    "orderCount": 0,
    "totalOrderValueCents": 0,
    "idempotencyKey": "user-a-sub-NEW"
  }' | jq
```

**Expected:** `409 Conflict` — "User already has an active subscription." This is the partial unique index + application guard working together.

---

## SCENE 4: User B — Gold Subscription (Needs Eligibility)

### Step 4.1 — User B tries Gold with insufficient order history (should FAIL)

Gold requires: 5+ orders OR ₹2000+ spend in last 30 days.

```bash
curl -s -X POST http://localhost:8080/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "bbbbbbbb-0000-0000-0000-000000000002",
    "planId": "a1b2c3d4-0001-0001-0001-000000000001",
    "tierId": "b1b2c3d4-0001-0001-0001-000000000002",
    "autoRenew": true,
    "orderCount": 2,
    "totalOrderValueCents": 50000,
    "idempotencyKey": "user-b-gold-fail"
  }' | jq
```

**Expected:** `422 Unprocessable Entity` — "None of the OR-group eligibility rules were satisfied."

### Step 4.2 — User B meets eligibility (5+ orders) and subscribes to Gold

```bash
curl -s -X POST http://localhost:8080/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "bbbbbbbb-0000-0000-0000-000000000002",
    "planId": "a1b2c3d4-0001-0001-0001-000000000001",
    "tierId": "b1b2c3d4-0001-0001-0001-000000000002",
    "autoRenew": true,
    "orderCount": 7,
    "totalOrderValueCents": 150000,
    "idempotencyKey": "user-b-gold-ok"
  }' | jq
```

**Expected:** `201 Created`, `status: ACTIVE`, `tierId` = Gold UUID.  
**Copy the returned `id` as `SUB_B_ID`.**

### Step 4.3 — Validate Gold benefits at checkout

```bash
curl -s -X POST http://localhost:8080/api/v1/benefits/validate \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "bbbbbbbb-0000-0000-0000-000000000002",
    "orderId": "22222222-0000-0000-0000-000000000001",
    "orderValueCents": 100000,
    "orderCategories": ["grocery", "dairy"],
    "deliveryRequested": true
  }' | jq
```

**Expected:** Better benefits than Silver:
- `freeDelivery: true` (Gold: no delivery minimum beyond ₹99)
- `discountCents: 10000` (Gold: 10% of ₹1000, capped at ₹200)
- `tierName: "Gold"`

---

## SCENE 5: User C — Platinum Eligibility Check

Platinum requires: 10+ orders AND ₹5000+ spend (strict AND).

### Step 5.1 — Fails AND rule: meets spend but not order count

```bash
curl -s -X POST http://localhost:8080/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "cccccccc-0000-0000-0000-000000000003",
    "planId": "a1b2c3d4-0001-0001-0001-000000000001",
    "tierId": "b1b2c3d4-0001-0001-0001-000000000003",
    "autoRenew": true,
    "orderCount": 5,
    "totalOrderValueCents": 600000,
    "idempotencyKey": "user-c-plat-fail"
  }' | jq
```

**Expected:** `422` — "Minimum order count not met. Required: 10, Current: 5"

### Step 5.2 — User C meets both AND criteria → Platinum subscription

```bash
curl -s -X POST http://localhost:8080/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "cccccccc-0000-0000-0000-000000000003",
    "planId": "a1b2c3d4-0001-0001-0001-000000000001",
    "tierId": "b1b2c3d4-0001-0001-0001-000000000003",
    "autoRenew": true,
    "orderCount": 12,
    "totalOrderValueCents": 650000,
    "idempotencyKey": "user-c-plat-ok"
  }' | jq
```

**Expected:** `201 Created`, `tierId` = Platinum UUID.  
**Copy the returned `id` as `SUB_C_ID`.**

### Step 5.3 — Validate Platinum benefits (best in class)

```bash
curl -s -X POST http://localhost:8080/api/v1/benefits/validate \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "cccccccc-0000-0000-0000-000000000003",
    "orderId": "33333333-0000-0000-0000-000000000001",
    "orderValueCents": 200000,
    "orderCategories": ["electronics"],
    "deliveryRequested": true
  }' | jq
```

**Expected:** 
- `freeDelivery: true` (no minimum)
- `discountCents: 30000` (15% of ₹2000, capped at ₹500)
- `tierName: "Platinum"`

### Step 5.4 — Non-member gets no benefits

```bash
curl -s -X POST http://localhost:8080/api/v1/benefits/validate \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "eeeeeeee-0000-0000-0000-000000000099",
    "orderId": "99999999-0000-0000-0000-000000000001",
    "orderValueCents": 100000,
    "orderCategories": ["dairy"],
    "deliveryRequested": true
  }' | jq
```

**Expected:** `eligible: false`, `freeDelivery: false`, `discountCents: 0`

---

## SCENE 6: User A — Upgrade from Silver to Gold

### Step 6.1 — Set User A's tier progress (simulate they've made 8 orders since subscribing)

```bash
# Replace <SUB_A_ID> with the actual subscription ID from Step 2.1
curl -s -X POST http://localhost:8080/api/v1/admin/users/aaaaaaaa-0000-0000-0000-000000000001/tier-progress \
  -H "Content-Type: application/json" \
  -d '{
    "subscriptionId": "<SUB_A_ID>",
    "orderCount": 8,
    "totalOrderValueCents": 300000
  }' | jq
```

### Step 6.2 — Upgrade User A from Silver → Gold

```bash
curl -s -X POST "http://localhost:8080/api/v1/subscriptions/<SUB_A_ID>/upgrade?userId=aaaaaaaa-0000-0000-0000-000000000001" \
  -H "Content-Type: application/json" \
  -d '{
    "targetTierId": "b1b2c3d4-0001-0001-0001-000000000002",
    "orderCount": 8,
    "totalOrderValueCents": 300000,
    "idempotencyKey": "user-a-upgrade-001"
  }' | jq
```

**Expected:** `status: ACTIVE`, `tierId` = Gold UUID. The prorated upgrade charge is calculated automatically.

### Step 6.3 — Validate User A now gets Gold benefits

```bash
curl -s -X POST http://localhost:8080/api/v1/benefits/validate \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "aaaaaaaa-0000-0000-0000-000000000001",
    "orderId": "11111111-0000-0000-0000-000000000099",
    "orderValueCents": 80000,
    "orderCategories": ["grocery"],
    "deliveryRequested": true
  }' | jq
```

**Expected:** Now showing Gold benefits — 10% discount.

---

## SCENE 7: User A — Schedule a Downgrade

### Step 7.1 — User A schedules downgrade back to Silver (abusing is prevented)

Downgrade is NOT immediate — it takes effect at period end.

```bash
curl -s -X POST "http://localhost:8080/api/v1/subscriptions/<SUB_A_ID>/downgrade?userId=aaaaaaaa-0000-0000-0000-000000000001" \
  -H "Content-Type: application/json" \
  -d '{
    "targetTierId": "b1b2c3d4-0001-0001-0001-000000000001",
    "idempotencyKey": "user-a-downgrade-001"
  }' | jq
```

**Expected:** `status: DOWNGRADE_SCHEDULED`, `scheduledTierId` = Silver UUID. User still keeps Gold benefits until `endDate`.

### Step 7.2 — Verify User A still gets Gold benefits during the downgrade period

```bash
curl -s -X POST http://localhost:8080/api/v1/benefits/validate \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "aaaaaaaa-0000-0000-0000-000000000001",
    "orderId": "11111111-0000-0000-0000-000000000100",
    "orderValueCents": 80000,
    "orderCategories": ["grocery"],
    "deliveryRequested": true
  }' | jq
```

**Expected:** Still Gold benefits — tier hasn't changed yet.

---

## SCENE 8: User D — Subscribe and Cancel

### Step 8.1 — User D subscribes to Monthly Silver

```bash
curl -s -X POST http://localhost:8080/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "dddddddd-0000-0000-0000-000000000004",
    "planId": "a1b2c3d4-0001-0001-0001-000000000001",
    "tierId": "b1b2c3d4-0001-0001-0001-000000000001",
    "autoRenew": false,
    "orderCount": 0,
    "totalOrderValueCents": 0,
    "idempotencyKey": "user-d-sub-001"
  }' | jq
```

**Copy `SUB_D_ID`.**

### Step 8.2 — User D cancels

```bash
curl -s -X POST "http://localhost:8080/api/v1/subscriptions/<SUB_D_ID>/cancel" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "dddddddd-0000-0000-0000-000000000004",
    "reason": "Found a better deal",
    "idempotencyKey": "user-d-cancel-001"
  }' | jq
```

**Expected:** `status: CANCELLED`, `cancelledAt` populated.

### Step 8.3 — Cancel again with same idempotency key (safe)

```bash
curl -s -X POST "http://localhost:8080/api/v1/subscriptions/<SUB_D_ID>/cancel" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "dddddddd-0000-0000-0000-000000000004",
    "reason": "Found a better deal",
    "idempotencyKey": "user-d-cancel-001"
  }' | jq
```

**Expected:** Same cancelled subscription returned — idempotent.

### Step 8.4 — User D tries to subscribe AGAIN after cancel (should work!)

After cancel, the user is free to re-subscribe.

```bash
curl -s -X POST http://localhost:8080/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "dddddddd-0000-0000-0000-000000000004",
    "planId": "a1b2c3d4-0001-0001-0001-000000000001",
    "tierId": "b1b2c3d4-0001-0001-0001-000000000001",
    "autoRenew": true,
    "orderCount": 0,
    "totalOrderValueCents": 0,
    "idempotencyKey": "user-d-sub-002"
  }' | jq
```

**Expected:** New subscription created successfully.

---

## SCENE 9: Admin — Add a Completely New Plan + Tier + Benefit

This demonstrates zero-code extensibility — add a new plan at runtime without touching application code.

### Step 9.1 — Create a new "Weekly" plan

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/plans \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Weekly",
    "durationDays": 7,
    "basePriceCents": 2900,
    "currency": "INR",
    "active": true
  }' | jq
```

**Copy the returned plan `id` as `WEEKLY_PLAN_ID`.**

### Step 9.2 — Add a "Basic" tier to the Weekly plan

```bash
curl -s -X POST "http://localhost:8080/api/v1/admin/plans/<WEEKLY_PLAN_ID>/tiers" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Basic",
    "rank": 1,
    "priceCents": 0,
    "active": true
  }' | jq
```

**Copy the returned tier `id` as `WEEKLY_BASIC_TIER_ID`.**

### Step 9.3 — Add free delivery benefit to Weekly Basic tier

```bash
curl -s -X POST "http://localhost:8080/api/v1/admin/tiers/<WEEKLY_BASIC_TIER_ID>/benefits" \
  -H "Content-Type: application/json" \
  -d '{
    "benefitType": "FREE_DELIVERY",
    "configJson": "{\"maxFreeDeliveriesPerMonth\": 2, \"minOrderValueCents\": 14900}",
    "active": true
  }' | jq
```

### Step 9.4 — Verify the new plan appears in the public catalog

```bash
curl -s http://localhost:8080/api/v1/plans | jq '.plans[] | select(.name == "Weekly")'
```

**Expected:** The Weekly plan now appears with its Basic tier and Free Delivery benefit.

### Step 9.5 — Subscribe a new user to the Weekly Basic plan

```bash
WEEKLY_USER="ffffffff-0000-0000-0000-000000000005"

curl -s -X POST http://localhost:8080/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"$WEEKLY_USER\",
    \"planId\": \"<WEEKLY_PLAN_ID>\",
    \"tierId\": \"<WEEKLY_BASIC_TIER_ID>\",
    \"autoRenew\": true,
    \"orderCount\": 0,
    \"totalOrderValueCents\": 0,
    \"idempotencyKey\": \"weekly-user-sub-001\"
  }" | jq
```

---

## SCENE 10: Add a New Eligibility Rule at Runtime

### Step 10.1 — Add a COHORT rule to Monthly Gold tier

This restricts Gold to only "early_adopter" and "premium" cohorts (in addition to existing OR rules).

```bash
curl -s -X POST "http://localhost:8080/api/v1/admin/tiers/b1b2c3d4-0001-0001-0001-000000000002/eligibility-rules" \
  -H "Content-Type: application/json" \
  -d '{
    "ruleType": "COHORT",
    "configJson": "{\"allowedCohorts\": [\"early_adopter\", \"premium\"]}",
    "operator": "OR"
  }' | jq
```

### Step 10.2 — Verify a non-cohort user still qualifies via ORDER_COUNT rule (OR logic)

```bash
curl -s -X POST http://localhost:8080/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "11111111-1111-1111-1111-111111111111",
    "planId": "a1b2c3d4-0001-0001-0001-000000000001",
    "tierId": "b1b2c3d4-0001-0001-0001-000000000002",
    "autoRenew": true,
    "orderCount": 6,
    "totalOrderValueCents": 100000,
    "userCohort": "regular",
    "idempotencyKey": "or-rule-test-001"
  }' | jq
```

**Expected:** `201 Created` — ORDER_COUNT OR rule passes (6 >= 5) even though COHORT fails.

### Step 10.3 — View all rules for a tier

```bash
curl -s "http://localhost:8080/api/v1/admin/tiers/b1b2c3d4-0001-0001-0001-000000000002/eligibility-rules" | jq
```

---

## SCENE 11: Benefit Comparison Table (Summary)

Run all three at checkout with the same order value to compare:

```bash
# Silver user (User A after downgrade takes effect — or check as Silver)
# 5% discount, limited free delivery
curl -s -X POST http://localhost:8080/api/v1/benefits/validate \
  -H "Content-Type: application/json" \
  -d '{"userId": "aaaaaaaa-0000-0000-0000-000000000001", "orderId": "aaa", "orderValueCents": 100000, "orderCategories": ["dairy"], "deliveryRequested": true}' \
  | jq '{tier: .tierName, discount: .discountCents, freeDelivery: .freeDelivery}'

# Gold user (User B)
curl -s -X POST http://localhost:8080/api/v1/benefits/validate \
  -H "Content-Type: application/json" \
  -d '{"userId": "bbbbbbbb-0000-0000-0000-000000000002", "orderId": "bbb", "orderValueCents": 100000, "orderCategories": ["dairy"], "deliveryRequested": true}' \
  | jq '{tier: .tierName, discount: .discountCents, freeDelivery: .freeDelivery}'

# Platinum user (User C)
curl -s -X POST http://localhost:8080/api/v1/benefits/validate \
  -H "Content-Type: application/json" \
  -d '{"userId": "cccccccc-0000-0000-0000-000000000003", "orderId": "ccc", "orderValueCents": 100000, "orderCategories": ["dairy"], "deliveryRequested": true}' \
  | jq '{tier: .tierName, discount: .discountCents, freeDelivery: .freeDelivery}'
```

**Expected comparison:**
| Tier | Discount | Free Delivery |
|------|----------|---------------|
| Silver | ₹50 (5%) | ✅ (min ₹199) |
| Gold | ₹100 (10%, capped) | ✅ (min ₹99) |
| Platinum | ₹150 (15%, capped) | ✅ (no minimum) |

---

## SCENE 12: Actuator Health

```bash
curl -s http://localhost:8080/actuator/health | jq

curl -s http://localhost:8080/actuator/metrics | jq '.names[]' | grep -i subscription
```

---

## Quick Reference: API Summary

| Method | Endpoint | What It Does |
|--------|----------|-------------|
| `GET` | `/api/v1/plans` | List all plans with tiers + benefits |
| `POST` | `/api/v1/subscriptions` | Subscribe (requires idempotencyKey) |
| `GET` | `/api/v1/subscriptions/me?userId=` | Get active subscription |
| `POST` | `/api/v1/subscriptions/{id}/upgrade` | Upgrade tier (prorated charge) |
| `POST` | `/api/v1/subscriptions/{id}/downgrade` | Schedule downgrade (end of period) |
| `POST` | `/api/v1/subscriptions/{id}/cancel` | Cancel subscription |
| `POST` | `/api/v1/benefits/validate` | Apply benefits at checkout |
| `GET` | `/api/v1/admin/plans` | List all plans (admin) |
| `POST` | `/api/v1/admin/plans` | Create new plan |
| `POST` | `/api/v1/admin/plans/{id}/tiers` | Add tier to plan |
| `POST` | `/api/v1/admin/tiers/{id}/benefits` | Add benefit to tier |
| `POST` | `/api/v1/admin/tiers/{id}/eligibility-rules` | Add eligibility rule |
| `DELETE` | `/api/v1/admin/eligibility-rules/{id}` | Remove eligibility rule |
| `POST` | `/api/v1/admin/users/{id}/tier-progress` | **[DEMO]** Set order count + spend |
| `PATCH` | `/api/v1/admin/plans/{id}/status?active=` | Activate/deactivate plan |
| `PATCH` | `/api/v1/admin/tiers/{id}/status?active=` | Activate/deactivate tier |

---

## Talking Points for the Interviewer

**Q: How is one-subscription-per-user enforced?**
> 3-layer defense: (1) application check at the start of the handler, (2) PostgreSQL partial unique index `WHERE status IN ('ACTIVE', ...)` — survives concurrent requests even when the application check races, (3) optimistic locking `@Version` column for preventing race conditions on existing rows.

**Q: How does idempotency work?**
> Every mutating endpoint requires an `idempotencyKey`. Before processing, we check the `idempotency_records` table. If found and not expired (24h TTL), return the cached response. The record is written in the **same transaction** as the subscription — so if the DB commit fails, no ghost idempotency record is left.

**Q: Why are downgrades scheduled instead of immediate?**
> To prevent benefit abuse: a user could subscribe to Platinum, use unlimited free delivery all month, then immediately downgrade. Scheduled downgrades ensure the user keeps the tier they paid for through the end of the period.

**Q: How do you add a new benefit type?**
> Zero code changes to existing classes. Implement `BenefitEvaluator`, annotate `@Component`, call `POST /api/v1/admin/tiers/{id}/benefits` with the new `benefitType` and a `configJson`. The composite evaluator auto-discovers the new bean via Spring's `List<BenefitEvaluator>` injection.

**Q: How does the rule engine handle AND vs OR?**
> Each `TierEligibilityRule` has an `operator` field (`AND`/`OR`). The `CompositeEligibilityEvaluator` groups them: all AND rules must pass, plus at least one OR rule must pass (if any OR rules exist). The seed data shows this: Gold uses OR (5+ orders OR ₹2000 spend), Platinum uses AND (10+ orders AND ₹5000 spend).

**Q: What happens when two requests try to subscribe the same user simultaneously?**
> Race condition is handled at the DB layer. Even if both requests pass the application-level active subscription check simultaneously, only one will succeed writing because of the partial unique index on `(user_id) WHERE status IN (...)`. The loser gets a DB constraint violation which surfaces as a `409 Conflict`.
