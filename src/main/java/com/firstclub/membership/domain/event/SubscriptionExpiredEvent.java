package com.firstclub.membership.domain.event;

import java.util.UUID;

public class SubscriptionExpiredEvent extends DomainEvent {

    private final UUID subscriptionId;
    private final UUID userId;

    public SubscriptionExpiredEvent(UUID subscriptionId, UUID userId) {
        super("SUBSCRIPTION_EXPIRED");
        this.subscriptionId = subscriptionId;
        this.userId = userId;
    }

    public UUID getSubscriptionId() { return subscriptionId; }
    public UUID getUserId() { return userId; }
}
