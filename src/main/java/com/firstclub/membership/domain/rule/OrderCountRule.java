package com.firstclub.membership.domain.rule;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * Eligibility rule: user must have placed at least N orders in the last M days.
 * Config: {"minOrders": 10, "periodDays": 30}
 */
@Component
public class OrderCountRule implements EligibilityRule {

    @Override
    public String ruleType() {
        return "ORDER_COUNT";
    }

    @Override
    public EligibilityResult evaluate(EligibilityContext context, JsonNode config) {
        int minOrders = config.path("minOrders").asInt(0);

        if (context.getOrderCount() >= minOrders) {
            return EligibilityResult.eligible();
        }

        return EligibilityResult.notEligible(
                String.format("Minimum order count not met. Required: %d, Current: %d",
                        minOrders, context.getOrderCount()));
    }
}
