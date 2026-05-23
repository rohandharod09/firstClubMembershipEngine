package com.firstclub.membership.domain.event;

import java.util.UUID;

public class SubscriptionCreatedEvent extends DomainEvent {

    private final UUID subscriptionId;
    private final UUID userId;
    private final UUID planId;
    private final UUID tierId;

    public SubscriptionCreatedEvent(UUID subscriptionId, UUID userId, UUID planId, UUID tierId) {
        super("SUBSCRIPTION_CREATED");
        this.subscriptionId = subscriptionId;
        this.userId = userId;
        this.planId = planId;
        this.tierId = tierId;
    }

    public UUID getSubscriptionId() { return subscriptionId; }
    public UUID getUserId() { return userId; }
    public UUID getPlanId() { return planId; }
    public UUID getTierId() { return tierId; }
}
