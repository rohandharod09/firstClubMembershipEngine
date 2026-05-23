package com.firstclub.membership.domain.event;

import java.util.UUID;

public class TierUpgradedEvent extends DomainEvent {

    private final UUID subscriptionId;
    private final UUID userId;
    private final UUID fromTierId;
    private final UUID toTierId;

    public TierUpgradedEvent(UUID subscriptionId, UUID userId, UUID fromTierId, UUID toTierId) {
        super("TIER_UPGRADED");
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
