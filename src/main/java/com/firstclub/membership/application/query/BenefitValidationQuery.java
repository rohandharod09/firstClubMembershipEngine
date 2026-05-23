package com.firstclub.membership.application.query;

import java.util.List;
import java.util.UUID;

public record BenefitValidationQuery(
        UUID userId,
        UUID orderId,
        long orderValueCents,
        List<String> orderCategories,
        boolean deliveryRequested
) {}
