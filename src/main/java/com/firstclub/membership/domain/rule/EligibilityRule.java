package com.firstclub.membership.domain.rule;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Strategy interface for tier eligibility rules.
 * Each implementation handles one rule type and is auto-discovered by the composite evaluator.
 * To add a new rule: implement this interface, annotate with @Component, insert a DB row.
 */
public interface EligibilityRule {

    /** Must match the ruleType stored in tier_eligibility_rules.rule_type */
    String ruleType();

    EligibilityResult evaluate(EligibilityContext context, JsonNode config);
}
