package com.firstclub.membership.api.rest.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record BenefitValidationRequest(
        @NotNull(message = "userId is required") UUID userId,
        @NotNull(message = "orderId is required") UUID orderId,
        long orderValueCents,
        List<String> orderCategories,
        boolean deliveryRequested
) {}
