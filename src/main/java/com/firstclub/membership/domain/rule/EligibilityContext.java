package com.firstclub.membership.domain.rule;

import java.util.UUID;

public class EligibilityContext {

    private final UUID userId;
    private final int orderCount;
    private final long totalOrderValueCents;
    private final String userCohort;
    private final UUID currentTierId;

    public EligibilityContext(UUID userId, int orderCount, long totalOrderValueCents,
                              String userCohort, UUID currentTierId) {
        this.userId = userId;
        this.orderCount = orderCount;
        this.totalOrderValueCents = totalOrderValueCents;
        this.userCohort = userCohort;
        this.currentTierId = currentTierId;
    }

    public UUID getUserId() { return userId; }
    public int getOrderCount() { return orderCount; }
    public long getTotalOrderValueCents() { return totalOrderValueCents; }
    public String getUserCohort() { return userCohort; }
    public UUID getCurrentTierId() { return currentTierId; }
}
