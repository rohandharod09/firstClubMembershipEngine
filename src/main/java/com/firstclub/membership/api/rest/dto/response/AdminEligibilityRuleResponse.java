package com.firstclub.membership.api.rest.dto.response;

import java.util.UUID;

public record AdminEligibilityRuleResponse(
        UUID id,
        UUID tierId,
        String ruleType,
        String configJson,
        String operator
) {}
