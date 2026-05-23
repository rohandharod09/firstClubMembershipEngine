package com.firstclub.membership.domain.rule;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * Eligibility rule: user must have spent at least X cents in the last M days.
 * Config: {"minValueCents": 50000, "periodDays": 30}
 */
@Component
public class OrderValueRule implements EligibilityRule {

    @Override
    public String ruleType() {
        return "ORDER_VALUE";
    }

    @Override
    public EligibilityResult evaluate(EligibilityContext context, JsonNode config) {
        long minValueCents = config.path("minValueCents").asLong(0);

        if (context.getTotalOrderValueCents() >= minValueCents) {
            return EligibilityResult.eligible();
        }

        return EligibilityResult.notEligible(
                String.format("Minimum order value not met. Required: %d cents, Current: %d cents",
                        minValueCents, context.getTotalOrderValueCents()));
    }
}
