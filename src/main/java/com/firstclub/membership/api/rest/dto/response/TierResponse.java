package com.firstclub.membership.api.rest.dto.response;

import java.util.List;
import java.util.UUID;

public record TierResponse(
        UUID id,
        String name,
        int rank,
        long priceCents,
        List<BenefitConfigResponse> benefits
) {
    public record BenefitConfigResponse(String type, Object config) {}
}
