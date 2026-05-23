package com.firstclub.membership.api.rest.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Used in demos to simulate a user's order history and tier progress.
 * In production, this would be populated by the Order Service via events.
 */
public record UpdateTierProgressRequest(
        @NotNull(message = "subscriptionId is required") UUID subscriptionId,
        @Min(0) int orderCount,
        @Min(0) long totalOrderValueCents
) {}
