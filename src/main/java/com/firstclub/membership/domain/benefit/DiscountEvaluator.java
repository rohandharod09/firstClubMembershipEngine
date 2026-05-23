package com.firstclub.membership.domain.benefit;

import com.fasterxml.jackson.databind.JsonNode;
import com.firstclub.membership.domain.model.BenefitType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates PERCENTAGE_DISCOUNT benefit.
 * Config: {
 *   "discountPercent": 10,
 *   "applicableCategories": ["all"] or ["dairy", "snacks"],
 *   "maxDiscountCents": 10000  (optional cap, -1 = unlimited)
 * }
 */
@Component
public class DiscountEvaluator implements BenefitEvaluator {

    @Override
    public BenefitType supportedType() {
        return BenefitType.PERCENTAGE_DISCOUNT;
    }

    @Override
    public BenefitResult evaluate(BenefitContext context, JsonNode config) {
        int discountPercent = config.path("discountPercent").asInt(0);
        if (discountPercent <= 0) {
            return BenefitResult.notApplied(BenefitType.PERCENTAGE_DISCOUNT,
                    "No discount percentage configured.");
        }

        // Check category applicability
        JsonNode categoriesNode = config.path("applicableCategories");
        List<String> applicableCategories = new ArrayList<>();
        if (categoriesNode.isArray()) {
            categoriesNode.forEach(n -> applicableCategories.add(n.asText()));
        }

        boolean categoryMatches = applicableCategories.isEmpty()
                || applicableCategories.contains("all")
                || context.getOrderCategories().stream().anyMatch(applicableCategories::contains);

        if (!categoryMatches) {
            return BenefitResult.notApplied(BenefitType.PERCENTAGE_DISCOUNT,
                    "No applicable categories in this order.");
        }

        long rawDiscount = (context.getOrderValueCents() * discountPercent) / 100;

        long maxDiscountCents = config.path("maxDiscountCents").asLong(-1);
        long finalDiscount = (maxDiscountCents > 0)
                ? Math.min(rawDiscount, maxDiscountCents)
                : rawDiscount;

        return BenefitResult.applied(BenefitType.PERCENTAGE_DISCOUNT, finalDiscount, false,
                String.format("%d%% discount applied: %d cents", discountPercent, finalDiscount));
    }
}
