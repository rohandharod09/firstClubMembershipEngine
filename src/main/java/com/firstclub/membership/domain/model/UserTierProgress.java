package com.firstclub.membership.domain.model;

import java.time.Instant;
import java.util.UUID;

public class UserTierProgress {

    private final UUID id;
    private final UUID userId;
    private final UUID subscriptionId;
    private final int orderCount;
    private final long totalOrderValueCents;
    private final Instant periodStart;
    private final Instant periodEnd;
    private final Instant evaluatedAt;

    public UserTierProgress(UUID id, UUID userId, UUID subscriptionId, int orderCount,
                            long totalOrderValueCents, Instant periodStart,
                            Instant periodEnd, Instant evaluatedAt) {
        this.id = id;
        this.userId = userId;
        this.subscriptionId = subscriptionId;
        this.orderCount = orderCount;
        this.totalOrderValueCents = totalOrderValueCents;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.evaluatedAt = evaluatedAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public int getOrderCount() { return orderCount; }
    public long getTotalOrderValueCents() { return totalOrderValueCents; }
    public Instant getPeriodStart() { return periodStart; }
    public Instant getPeriodEnd() { return periodEnd; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
}
