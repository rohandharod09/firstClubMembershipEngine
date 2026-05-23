package com.firstclub.membership.domain.event;

import java.util.UUID;

public class SubscriptionCancelledEvent extends DomainEvent {

    private final UUID subscriptionId;
    private final UUID userId;
    private final String reason;

    public SubscriptionCancelledEvent(UUID subscriptionId, UUID userId, String reason) {
        super("SUBSCRIPTION_CANCELLED");
        this.subscriptionId = subscriptionId;
        this.userId = userId;
        this.reason = reason;
    }

    public UUID getSubscriptionId() { return subscriptionId; }
    public UUID getUserId() { return userId; }
    public String getReason() { return reason; }
}
