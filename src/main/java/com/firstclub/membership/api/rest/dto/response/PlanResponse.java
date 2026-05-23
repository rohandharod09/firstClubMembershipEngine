package com.firstclub.membership.api.rest.dto.response;

import java.util.List;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        String name,
        int durationDays,
        long basePriceCents,
        String currency,
        List<TierResponse> tiers
) {}
