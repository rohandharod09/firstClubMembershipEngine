package com.firstclub.membership.api.rest.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubscribeRequest(
        @NotNull(message = "userId is required") UUID userId,
        @NotNull(message = "planId is required") UUID planId,
        @NotNull(message = "tierId is required") UUID tierId,
        boolean autoRenew,
        String userCohort,
        int orderCount,
        long totalOrderValueCents,
        @NotNull(message = "idempotencyKey is required") String idempotencyKey
) {}
