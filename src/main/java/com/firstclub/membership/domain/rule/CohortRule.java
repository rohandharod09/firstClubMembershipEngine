package com.firstclub.membership.domain.rule;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Eligibility rule: user must belong to one of the specified cohorts.
 * Config: {"allowedCohorts": ["early_adopter", "beta", "vip"]}
 */
@Component
public class CohortRule implements EligibilityRule {

    @Override
    public String ruleType() {
        return "COHORT";
    }

    @Override
    public EligibilityResult evaluate(EligibilityContext context, JsonNode config) {
        JsonNode cohortsNode = config.path("allowedCohorts");
        if (!cohortsNode.isArray() || cohortsNode.isEmpty()) {
            return EligibilityResult.eligible();
        }

        List<String> allowedCohorts = new ArrayList<>();
        cohortsNode.forEach(node -> allowedCohorts.add(node.asText()));

        String userCohort = context.getUserCohort();
        if (userCohort != null && allowedCohorts.contains(userCohort)) {
            return EligibilityResult.eligible();
        }

        return EligibilityResult.notEligible(
                String.format("User cohort '%s' is not in the allowed cohorts: %s",
                        userCohort, allowedCohorts));
    }
}
