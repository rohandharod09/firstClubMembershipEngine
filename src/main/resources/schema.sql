-- ============================================================
-- FirstClub Membership Engine — Database Schema
-- All tables use IF NOT EXISTS for idempotent startup.
-- Monetary values stored in cents (BIGINT) — no floating point.
-- JSONB for extensible config columns (benefits, rules).
-- ============================================================

-- Membership Plans (Monthly, Quarterly, Yearly)
CREATE TABLE IF NOT EXISTS membership_plans (
    id            UUID         PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    duration_days INT          NOT NULL,
    base_price_cents BIGINT    NOT NULL CHECK (base_price_cents >= 0),
    currency      VARCHAR(3)   NOT NULL DEFAULT 'INR',
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Membership Tiers (Silver, Gold, Platinum)
CREATE TABLE IF NOT EXISTS membership_tiers (
    id           UUID         PRIMARY KEY,
    plan_id      UUID         NOT NULL REFERENCES membership_plans(id),
    name         VARCHAR(50)  NOT NULL,
    rank         INT          NOT NULL CHECK (rank > 0),
    price_cents  BIGINT       NOT NULL CHECK (price_cents >= 0),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Tier Benefits (configurable per tier, JSONB config)
CREATE TABLE IF NOT EXISTS tier_benefits (
    id           UUID         PRIMARY KEY,
    tier_id      UUID         NOT NULL REFERENCES membership_tiers(id),
    benefit_type VARCHAR(50)  NOT NULL,
    config_json  JSONB        NOT NULL DEFAULT '{}',
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Tier Eligibility Rules (configurable, AND/OR combinable)
CREATE TABLE IF NOT EXISTS tier_eligibility_rules (
    id           UUID         PRIMARY KEY,
    tier_id      UUID         NOT NULL REFERENCES membership_tiers(id),
    rule_type    VARCHAR(50)  NOT NULL,
    config_json  JSONB        NOT NULL DEFAULT '{}',
    operator     VARCHAR(3)   NOT NULL DEFAULT 'AND' CHECK (operator IN ('AND', 'OR')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- User Subscriptions (aggregate root — optimistic lock via version column)
CREATE TABLE IF NOT EXISTS user_subscriptions (
    id                UUID        PRIMARY KEY,
    user_id           UUID        NOT NULL,
    plan_id           UUID        NOT NULL REFERENCES membership_plans(id),
    tier_id           UUID        NOT NULL REFERENCES membership_tiers(id),
    status            VARCHAR(30) NOT NULL,
    start_date        TIMESTAMPTZ NOT NULL,
    end_date          TIMESTAMPTZ NOT NULL,
    auto_renew        BOOLEAN     NOT NULL DEFAULT TRUE,
    cancelled_at      TIMESTAMPTZ,
    previous_tier_id  UUID,
    scheduled_tier_id UUID,
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- User Tier Progress (tracks orders/spend for eligibility evaluation)
CREATE TABLE IF NOT EXISTS user_tier_progress (
    id                     UUID        PRIMARY KEY,
    user_id                UUID        NOT NULL,
    subscription_id        UUID        NOT NULL REFERENCES user_subscriptions(id),
    order_count            INT         NOT NULL DEFAULT 0,
    total_order_value_cents BIGINT     NOT NULL DEFAULT 0,
    period_start           TIMESTAMPTZ NOT NULL,
    period_end             TIMESTAMPTZ NOT NULL,
    evaluated_at           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Payment Transactions
CREATE TABLE IF NOT EXISTS payment_transactions (
    id               UUID        PRIMARY KEY,
    subscription_id  UUID        NOT NULL REFERENCES user_subscriptions(id),
    user_id          UUID        NOT NULL,
    amount_cents     BIGINT      NOT NULL CHECK (amount_cents >= 0),
    currency         VARCHAR(3)  NOT NULL DEFAULT 'INR',
    status           VARCHAR(20) NOT NULL,
    external_txn_id  VARCHAR(255),
    idempotency_key  VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Idempotency Records (cache API responses for dedup)
CREATE TABLE IF NOT EXISTS idempotency_records (
    id               UUID        PRIMARY KEY,
    idempotency_key  VARCHAR(255) NOT NULL,
    resource_type    VARCHAR(50),
    resource_id      UUID,
    response_payload JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at       TIMESTAMPTZ NOT NULL
);

-- ShedLock table (for distributed scheduler locking)
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- ============================================================
-- INDEXES
-- ============================================================

-- Partial unique index: one active subscription per user (THE key concurrency guard)
CREATE UNIQUE INDEX IF NOT EXISTS idx_one_active_sub_per_user
    ON user_subscriptions (user_id)
    WHERE status IN ('ACTIVE', 'GRACE_PERIOD', 'PENDING_PAYMENT', 'UPGRADE_PENDING', 'DOWNGRADE_SCHEDULED');

-- Expiry scheduler batch query
CREATE INDEX IF NOT EXISTS idx_sub_status_end_date
    ON user_subscriptions (status, end_date);

-- Active subscription lookup (hot path: benefit validation at checkout)
CREATE INDEX IF NOT EXISTS idx_sub_user_status
    ON user_subscriptions (user_id, status);

-- Payment deduplication
CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_idempotency
    ON payment_transactions (idempotency_key);

-- API request deduplication
CREATE UNIQUE INDEX IF NOT EXISTS idx_idempotency_key
    ON idempotency_records (idempotency_key);

-- Benefit lookup for a tier
CREATE INDEX IF NOT EXISTS idx_tier_benefits_tier_active
    ON tier_benefits (tier_id, active);

-- Tier listing for a plan
CREATE INDEX IF NOT EXISTS idx_tiers_plan_active
    ON membership_tiers (plan_id, active);

-- Tier progress lookup by user
CREATE INDEX IF NOT EXISTS idx_tier_progress_user
    ON user_tier_progress (user_id, subscription_id);
