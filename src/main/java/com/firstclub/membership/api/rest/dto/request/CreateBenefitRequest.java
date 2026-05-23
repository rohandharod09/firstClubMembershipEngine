package com.firstclub.membership.api.rest.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateBenefitRequest(
        @NotBlank(message = "benefitType is required") String benefitType,
        @NotBlank(message = "configJson is required") String configJson,
        boolean active
) {}
