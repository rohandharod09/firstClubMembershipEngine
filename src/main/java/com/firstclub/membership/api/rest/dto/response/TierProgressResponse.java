package com.firstclub.membership.api.rest.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TierProgressResponse(
        UUID userId,
        UUID subscriptionId,
        int orderCount,
        long totalOrderValueCents,
        Instant periodStart,
        Instant periodEnd,
        Instant evaluatedAt
) {}
