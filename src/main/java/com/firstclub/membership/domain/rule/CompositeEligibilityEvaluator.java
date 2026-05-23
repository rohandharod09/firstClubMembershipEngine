package com.firstclub.membership.domain.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstclub.membership.common.exception.EligibilityNotMetException;
import com.firstclub.membership.domain.model.TierEligibilityRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Composite evaluator that applies all eligibility rules for a tier.
 * Rules grouped by operator: AND-group (all must pass), OR-group (any must pass).
 * New rules auto-register by implementing EligibilityRule + @Component.
 */
@Component
public class CompositeEligibilityEvaluator {

    private static final Logger log = LoggerFactory.getLogger(CompositeEligibilityEvaluator.class);

    private final Map<String, EligibilityRule> ruleRegistry;
    private final ObjectMapper objectMapper;

    public CompositeEligibilityEvaluator(List<EligibilityRule> rules, ObjectMapper objectMapper) {
        this.ruleRegistry = rules.stream()
                .collect(Collectors.toMap(EligibilityRule::ruleType, Function.identity()));
        this.objectMapper = objectMapper;
        log.info("Registered {} eligibility rules: {}", rules.size(), ruleRegistry.keySet());
    }

    /**
     * Evaluate all rules for a target tier. Throws EligibilityNotMetException if any fails.
     *
     * @param context    the user's eligibility context
     * @param tierRules  the rules configured for the target tier (loaded from DB)
     */
    public void evaluate(EligibilityContext context, List<TierEligibilityRule> tierRules) {
        if (tierRules == null || tierRules.isEmpty()) {
            return;
        }

        List<TierEligibilityRule> andRules = tierRules.stream()
                .filter(r -> !"OR".equalsIgnoreCase(r.getOperator()))
                .collect(Collectors.toList());

        List<TierEligibilityRule> orRules = tierRules.stream()
                .filter(r -> "OR".equalsIgnoreCase(r.getOperator()))
                .collect(Collectors.toList());

        // All AND rules must pass
        for (TierEligibilityRule rule : andRules) {
            EligibilityResult result = evaluateSingle(context, rule);
            if (!result.isEligible()) {
                throw new EligibilityNotMetException(result.getReason());
            }
        }

        // At least one OR rule must pass (if any OR rules exist)
        if (!orRules.isEmpty()) {
            boolean anyOrPassed = orRules.stream()
                    .anyMatch(rule -> evaluateSingle(context, rule).isEligible());
            if (!anyOrPassed) {
                throw new EligibilityNotMetException(
                        "None of the OR-group eligibility rules were satisfied.");
            }
        }
    }

    private EligibilityResult evaluateSingle(EligibilityContext context, TierEligibilityRule rule) {
        EligibilityRule evaluator = ruleRegistry.get(rule.getRuleType());
        if (evaluator == null) {
            log.warn("No evaluator registered for rule type '{}'. Skipping.", rule.getRuleType());
            return EligibilityResult.eligible();
        }

        try {
            JsonNode config = objectMapper.readTree(rule.getConfigJson());
            return evaluator.evaluate(context, config);
        } catch (Exception e) {
            log.error("Error evaluating rule '{}': {}", rule.getRuleType(), e.getMessage());
            return EligibilityResult.notEligible("Rule evaluation error: " + rule.getRuleType());
        }
    }
}
