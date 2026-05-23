package com.firstclub.membership.domain.benefit;

import com.fasterxml.jackson.databind.JsonNode;
import com.firstclub.membership.domain.model.BenefitType;
import org.springframework.stereotype.Component;

/**
 * Evaluates FREE_DELIVERY benefit.
 * Config: {"maxFreeDeliveriesPerMonth": 5, "minOrderValueCents": 20000}
 * - maxFreeDeliveriesPerMonth: -1 means unlimited
 * - minOrderValueCents: 0 means no minimum
 */
@Component
public class FreeDeliveryEvaluator implements BenefitEvaluator {

    @Override
    public BenefitType supportedType() {
        return BenefitType.FREE_DELIVERY;
    }

    @Override
    public BenefitResult evaluate(BenefitContext context, JsonNode config) {
        if (!context.isDeliveryRequested()) {
            return BenefitResult.notApplied(BenefitType.FREE_DELIVERY,
                    "Delivery not requested for this order.");
        }

        long minOrderValueCents = config.path("minOrderValueCents").asLong(0);
        if (context.getOrderValueCents() < minOrderValueCents) {
            return BenefitResult.notApplied(BenefitType.FREE_DELIVERY,
                    String.format("Order value %d cents is below minimum %d cents for free delivery.",
                            context.getOrderValueCents(), minOrderValueCents));
        }

        return BenefitResult.applied(BenefitType.FREE_DELIVERY, 0, true,
                "Free delivery applied.");
    }
}
