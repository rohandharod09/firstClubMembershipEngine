package com.firstclub.membership.api.rest.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AdminPlanResponse(
        UUID id,
        String name,
        int durationDays,
        long basePriceCents,
        String currency,
        boolean active,
        Instant createdAt
) {}
