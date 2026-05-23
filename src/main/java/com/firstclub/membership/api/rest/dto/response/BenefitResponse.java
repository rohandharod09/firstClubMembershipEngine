package com.firstclub.membership.api.rest.dto.response;

public record BenefitResponse(
        String type,
        boolean applied,
        long discountCents,
        String description
) {}
