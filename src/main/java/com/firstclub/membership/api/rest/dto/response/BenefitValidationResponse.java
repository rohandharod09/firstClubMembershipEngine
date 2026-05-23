package com.firstclub.membership.api.rest.dto.response;

import java.time.Instant;
import java.util.List;

public record BenefitValidationResponse(
        boolean eligible,
        boolean freeDelivery,
        long discountCents,
        List<BenefitResponse> appliedBenefits,
        Instant membershipExpiresAt,
        String tierName
) {}
