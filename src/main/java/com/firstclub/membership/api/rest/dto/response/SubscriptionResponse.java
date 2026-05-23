package com.firstclub.membership.api.rest.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID userId,
        UUID planId,
        UUID tierId,
        String status,
        Instant startDate,
        Instant endDate,
        boolean autoRenew,
        Instant cancelledAt,
        UUID scheduledTierId,
        Instant createdAt,
        Instant updatedAt
) {}
