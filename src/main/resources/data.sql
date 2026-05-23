-- ============================================================
-- FirstClub Membership Engine — Seed Data
-- Uses INSERT ... ON CONFLICT DO NOTHING for idempotent re-runs.
-- ============================================================

-- ============================================================
-- MEMBERSHIP PLANS
-- ============================================================

INSERT INTO membership_plans (id, name, duration_days, base_price_cents, currency, active)
VALUES
    ('a1b2c3d4-0001-0001-0001-000000000001', 'Monthly',   30,  9900,  'INR', true),
    ('a1b2c3d4-0001-0001-0001-000000000002', 'Quarterly', 90,  24900, 'INR', true),
    ('a1b2c3d4-0001-0001-0001-000000000003', 'Yearly',    365, 89900, 'INR', true)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- MEMBERSHIP TIERS — Monthly Plan
-- ============================================================

INSERT INTO membership_tiers (id, plan_id, name, rank, price_cents, active)
VALUES
    -- Monthly Silver
    ('b1b2c3d4-0001-0001-0001-000000000001',
     'a1b2c3d4-0001-0001-0001-000000000001', 'Silver', 1, 0, true),
    -- Monthly Gold
    ('b1b2c3d4-0001-0001-0001-000000000002',
     'a1b2c3d4-0001-0001-0001-000000000001', 'Gold',   2, 5000, true),
    -- Monthly Platinum
    ('b1b2c3d4-0001-0001-0001-000000000003',
     'a1b2c3d4-0001-0001-0001-000000000001', 'Platinum', 3, 15000, true),

    -- Quarterly Silver
    ('b1b2c3d4-0002-0001-0001-000000000001',
     'a1b2c3d4-0001-0001-0001-000000000002', 'Silver', 1, 0, true),
    -- Quarterly Gold
    ('b1b2c3d4-0002-0001-0001-000000000002',
     'a1b2c3d4-0001-0001-0001-000000000002', 'Gold',   2, 12000, true),
    -- Quarterly Platinum
    ('b1b2c3d4-0002-0001-0001-000000000003',
     'a1b2c3d4-0001-0001-0001-000000000002', 'Platinum', 3, 35000, true),

    -- Yearly Silver
    ('b1b2c3d4-0003-0001-0001-000000000001',
     'a1b2c3d4-0001-0001-0001-000000000003', 'Silver', 1, 0, true),
    -- Yearly Gold
    ('b1b2c3d4-0003-0001-0001-000000000002',
     'a1b2c3d4-0001-0001-0001-000000000003', 'Gold',   2, 40000, true),
    -- Yearly Platinum
    ('b1b2c3d4-0003-0001-0001-000000000003',
     'a1b2c3d4-0001-0001-0001-000000000003', 'Platinum', 3, 120000, true)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- TIER BENEFITS — Monthly Silver
-- ============================================================

INSERT INTO tier_benefits (id, tier_id, benefit_type, config_json, active)
VALUES
    -- Monthly Silver: 3 free deliveries/month, min order Rs 199
    ('c1b2c3d4-0001-0001-0001-000000000001',
     'b1b2c3d4-0001-0001-0001-000000000001',
     'FREE_DELIVERY',
     '{"maxFreeDeliveriesPerMonth": 3, "minOrderValueCents": 19900}',
     true),
    -- Monthly Silver: 5% discount on all categories
    ('c1b2c3d4-0001-0001-0001-000000000002',
     'b1b2c3d4-0001-0001-0001-000000000001',
     'PERCENTAGE_DISCOUNT',
     '{"discountPercent": 5, "applicableCategories": ["all"], "maxDiscountCents": 5000}',
     true),

-- ============================================================
-- TIER BENEFITS — Monthly Gold
-- ============================================================

    -- Monthly Gold: unlimited free deliveries, min order Rs 99
    ('c1b2c3d4-0001-0002-0001-000000000001',
     'b1b2c3d4-0001-0001-0001-000000000002',
     'FREE_DELIVERY',
     '{"maxFreeDeliveriesPerMonth": -1, "minOrderValueCents": 9900}',
     true),
    -- Monthly Gold: 10% discount, cap Rs 200
    ('c1b2c3d4-0001-0002-0001-000000000002',
     'b1b2c3d4-0001-0001-0001-000000000002',
     'PERCENTAGE_DISCOUNT',
     '{"discountPercent": 10, "applicableCategories": ["all"], "maxDiscountCents": 20000}',
     true),
    -- Monthly Gold: early access to sales
    ('c1b2c3d4-0001-0002-0001-000000000003',
     'b1b2c3d4-0001-0001-0001-000000000002',
     'EARLY_ACCESS',
     '{"hoursBeforeSale": 24}',
     true),

-- ============================================================
-- TIER BENEFITS — Monthly Platinum
-- ============================================================

    -- Monthly Platinum: unlimited free delivery, no minimum
    ('c1b2c3d4-0001-0003-0001-000000000001',
     'b1b2c3d4-0001-0001-0001-000000000003',
     'FREE_DELIVERY',
     '{"maxFreeDeliveriesPerMonth": -1, "minOrderValueCents": 0}',
     true),
    -- Monthly Platinum: 15% discount, cap Rs 500
    ('c1b2c3d4-0001-0003-0001-000000000002',
     'b1b2c3d4-0001-0001-0001-000000000003',
     'PERCENTAGE_DISCOUNT',
     '{"discountPercent": 15, "applicableCategories": ["all"], "maxDiscountCents": 50000}',
     true),
    -- Monthly Platinum: exclusive deals
    ('c1b2c3d4-0001-0003-0001-000000000003',
     'b1b2c3d4-0001-0001-0001-000000000003',
     'EXCLUSIVE_DEAL',
     '{"dealCodes": ["PLAT10", "PLAT20"], "description": "Exclusive Platinum member deals"}',
     true),
    -- Monthly Platinum: priority support
    ('c1b2c3d4-0001-0003-0001-000000000004',
     'b1b2c3d4-0001-0001-0001-000000000003',
     'PRIORITY_SUPPORT',
     '{"supportTier": "platinum", "maxWaitMinutes": 5}',
     true)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- TIER ELIGIBILITY RULES
-- Silver: no eligibility requirements (open to all)
-- Gold: >= 5 orders OR spend >= Rs 2000 in last 30 days
-- Platinum: >= 10 orders AND spend >= Rs 5000 in last 30 days
-- ============================================================

INSERT INTO tier_eligibility_rules (id, tier_id, rule_type, config_json, operator)
VALUES
    -- Monthly Gold: 5+ orders (OR)
    ('d1b2c3d4-0001-0002-0001-000000000001',
     'b1b2c3d4-0001-0001-0001-000000000002',
     'ORDER_COUNT',
     '{"minOrders": 5, "periodDays": 30}',
     'OR'),
    -- Monthly Gold: Rs 2000+ spend (OR)
    ('d1b2c3d4-0001-0002-0001-000000000002',
     'b1b2c3d4-0001-0001-0001-000000000002',
     'ORDER_VALUE',
     '{"minValueCents": 200000, "periodDays": 30}',
     'OR'),

    -- Monthly Platinum: 10+ orders (AND)
    ('d1b2c3d4-0001-0003-0001-000000000001',
     'b1b2c3d4-0001-0001-0001-000000000003',
     'ORDER_COUNT',
     '{"minOrders": 10, "periodDays": 30}',
     'AND'),
    -- Monthly Platinum: Rs 5000+ spend (AND)
    ('d1b2c3d4-0001-0003-0001-000000000002',
     'b1b2c3d4-0001-0001-0001-000000000003',
     'ORDER_VALUE',
     '{"minValueCents": 500000, "periodDays": 30}',
     'AND'),

    -- Quarterly Gold: same rules
    ('d1b2c3d4-0002-0002-0001-000000000001',
     'b1b2c3d4-0002-0001-0001-000000000002',
     'ORDER_COUNT',
     '{"minOrders": 5, "periodDays": 30}',
     'OR'),
    ('d1b2c3d4-0002-0002-0001-000000000002',
     'b1b2c3d4-0002-0001-0001-000000000002',
     'ORDER_VALUE',
     '{"minValueCents": 200000, "periodDays": 30}',
     'OR'),

    -- Quarterly Platinum: same rules
    ('d1b2c3d4-0002-0003-0001-000000000001',
     'b1b2c3d4-0002-0001-0001-000000000003',
     'ORDER_COUNT',
     '{"minOrders": 10, "periodDays": 30}',
     'AND'),
    ('d1b2c3d4-0002-0003-0001-000000000002',
     'b1b2c3d4-0002-0001-0001-000000000003',
     'ORDER_VALUE',
     '{"minValueCents": 500000, "periodDays": 30}',
     'AND'),

    -- Yearly Gold
    ('d1b2c3d4-0003-0002-0001-000000000001',
     'b1b2c3d4-0003-0001-0001-000000000002',
     'ORDER_COUNT',
     '{"minOrders": 5, "periodDays": 30}',
     'OR'),
    ('d1b2c3d4-0003-0002-0001-000000000002',
     'b1b2c3d4-0003-0001-0001-000000000002',
     'ORDER_VALUE',
     '{"minValueCents": 200000, "periodDays": 30}',
     'OR'),

    -- Yearly Platinum
    ('d1b2c3d4-0003-0003-0001-000000000001',
     'b1b2c3d4-0003-0001-0001-000000000003',
     'ORDER_COUNT',
     '{"minOrders": 10, "periodDays": 30}',
     'AND'),
    ('d1b2c3d4-0003-0003-0001-000000000002',
     'b1b2c3d4-0003-0001-0001-000000000003',
     'ORDER_VALUE',
     '{"minValueCents": 500000, "periodDays": 30}',
     'AND')
ON CONFLICT (id) DO NOTHING;
