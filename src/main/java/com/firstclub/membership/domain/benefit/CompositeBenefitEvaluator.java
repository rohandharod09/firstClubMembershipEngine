package com.firstclub.membership.domain.benefit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstclub.membership.domain.model.BenefitType;
import com.firstclub.membership.domain.model.TierBenefit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Composite evaluator that applies all active benefits for a user's tier.
 * Aggregates discount cents and free delivery flag across all applicable benefits.
 */
@Component
public class CompositeBenefitEvaluator {

    private static final Logger log = LoggerFactory.getLogger(CompositeBenefitEvaluator.class);

    private final Map<BenefitType, BenefitEvaluator> evaluatorRegistry;
    private final ObjectMapper objectMapper;

    public CompositeBenefitEvaluator(List<BenefitEvaluator> evaluators, ObjectMapper objectMapper) {
        this.evaluatorRegistry = evaluators.stream()
                .collect(Collectors.toMap(BenefitEvaluator::supportedType, Function.identity()));
        this.objectMapper = objectMapper;
        log.info("Registered {} benefit evaluators: {}", evaluators.size(),
                evaluatorRegistry.keySet());
    }

    public AggregatedBenefitResult evaluate(BenefitContext context, List<TierBenefit> tierBenefits) {
        List<BenefitResult> results = new ArrayList<>();
        long totalDiscountCents = 0;
        boolean freeDelivery = false;

        for (TierBenefit benefit : tierBenefits) {
            if (!benefit.isActive()) continue;

            BenefitEvaluator evaluator = evaluatorRegistry.get(benefit.getBenefitType());
            if (evaluator == null) {
                log.warn("No evaluator for benefit type '{}'. Skipping.", benefit.getBenefitType());
                continue;
            }

            try {
                JsonNode config = objectMapper.readTree(benefit.getConfigJson());
                BenefitResult result = evaluator.evaluate(context, config);
                results.add(result);

                if (result.isApplied()) {
                    totalDiscountCents += result.getDiscountCents();
                    if (result.isFreeDelivery()) {
                        freeDelivery = true;
                    }
                }
            } catch (Exception e) {
                log.error("Error evaluating benefit '{}': {}", benefit.getBenefitType(),
                        e.getMessage());
            }
        }

        return new AggregatedBenefitResult(results, totalDiscountCents, freeDelivery);
    }

    public record AggregatedBenefitResult(
            List<BenefitResult> individualResults,
            long totalDiscountCents,
            boolean freeDelivery
    ) {}
}
