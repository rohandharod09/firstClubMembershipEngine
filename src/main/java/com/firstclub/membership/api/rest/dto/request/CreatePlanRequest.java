package com.firstclub.membership.api.rest.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePlanRequest(
        @NotBlank(message = "name is required") String name,
        @Min(value = 1, message = "durationDays must be at least 1") int durationDays,
        @Min(value = 0, message = "basePriceCents must be non-negative") long basePriceCents,
        @NotBlank(message = "currency is required") String currency,
        boolean active
) {}
