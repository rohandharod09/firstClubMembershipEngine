package com.firstclub.membership.domain.benefit;

import java.util.List;
import java.util.UUID;

public class BenefitContext {

    private final UUID userId;
    private final UUID orderId;
    private final long orderValueCents;
    private final List<String> orderCategories;
    private final boolean deliveryRequested;
    private final String tierName;

    public BenefitContext(UUID userId, UUID orderId, long orderValueCents,
                          List<String> orderCategories, boolean deliveryRequested,
                          String tierName) {
        this.userId = userId;
        this.orderId = orderId;
        this.orderValueCents = orderValueCents;
        this.orderCategories = orderCategories != null ? List.copyOf(orderCategories) : List.of();
        this.deliveryRequested = deliveryRequested;
        this.tierName = tierName;
    }

    public UUID getUserId() { return userId; }
    public UUID getOrderId() { return orderId; }
    public long getOrderValueCents() { return orderValueCents; }
    public List<String> getOrderCategories() { return orderCategories; }
    public boolean isDeliveryRequested() { return deliveryRequested; }
    public String getTierName() { return tierName; }
}
