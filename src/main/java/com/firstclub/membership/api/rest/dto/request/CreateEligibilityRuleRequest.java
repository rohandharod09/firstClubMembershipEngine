package com.firstclub.membership.api.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateEligibilityRuleRequest(
        @NotBlank(message = "ruleType is required") String ruleType,
        @NotBlank(message = "configJson is required") String configJson,
        @NotBlank(message = "operator is required")
        @Pattern(regexp = "AND|OR", message = "operator must be AND or OR")
        String operator
) {}
