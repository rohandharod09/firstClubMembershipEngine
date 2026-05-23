package com.firstclub.membership.domain.event;

import java.util.UUID;

public class TierDowngradedEvent extends DomainEvent {

    private final UUID subscriptionId;
    private final UUID userId;
    private final UUID fromTierId;
    private final UUID toTierId;

    public TierDowngradedEvent(UUID subscriptionId, UUID userId, UUID fromTierId, UUID toTierId) {
        super("TIER_DOWNGRADED");
        this.subscriptionId = subscriptionId;
        this.userId = userId;
        this.fromTierId = fromTierId;
        this.toTierId = toTierId;
    }

    public UUID getSubscriptionId() { return subscriptionId; }
    public UUID getUserId() { return userId; }
    public UUID getFromTierId() { return fromTierId; }
    public UUID getToTierId() { return toTierId; }
}
