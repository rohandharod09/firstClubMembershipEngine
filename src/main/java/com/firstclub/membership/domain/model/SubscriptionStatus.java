package com.firstclub.membership.domain.model;

public enum SubscriptionStatus {
    PENDING_PAYMENT,
    ACTIVE,
    UPGRADE_PENDING,
    DOWNGRADE_SCHEDULED,
    CANCELLED,
    GRACE_PERIOD,
    EXPIRED,
    PAYMENT_FAILED
}
