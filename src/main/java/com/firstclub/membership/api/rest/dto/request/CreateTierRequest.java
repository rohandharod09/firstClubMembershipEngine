package com.firstclub.membership.api.rest.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateTierRequest(
        @NotBlank(message = "name is required") String name,
        @Min(value = 1, message = "rank must be at least 1") int rank,
        @Min(value = 0, message = "priceCents must be non-negative") long priceCents,
        boolean active
) {}
