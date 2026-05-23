package com.firstclub.membership.api.rest.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AdminTierResponse(
        UUID id,
        UUID planId,
        String name,
        int rank,
        long priceCents,
        boolean active,
        Instant createdAt
) {}
