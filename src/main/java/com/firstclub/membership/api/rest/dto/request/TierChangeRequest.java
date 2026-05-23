package com.firstclub.membership.api.rest.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TierChangeRequest(
        @NotNull(message = "targetTierId is required") UUID targetTierId,
        String userCohort,
        int orderCount,
        long totalOrderValueCents,
        @NotNull(message = "idempotencyKey is required") String idempotencyKey
) {}
