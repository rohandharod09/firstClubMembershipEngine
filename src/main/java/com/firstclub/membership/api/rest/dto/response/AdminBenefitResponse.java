package com.firstclub.membership.api.rest.dto.response;

import java.util.UUID;

public record AdminBenefitResponse(
        UUID id,
        UUID tierId,
        String benefitType,
        String configJson,
        boolean active
) {}
