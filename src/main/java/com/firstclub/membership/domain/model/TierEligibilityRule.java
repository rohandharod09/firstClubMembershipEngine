package com.firstclub.membership.domain.model;

import java.util.UUID;

public class TierEligibilityRule {

    private final UUID id;
    private final UUID tierId;
    private final String ruleType;
    private final String configJson;
    private final String operator;

    public TierEligibilityRule(UUID id, UUID tierId, String ruleType, String configJson, String operator) {
        this.id = id;
        this.tierId = tierId;
        this.ruleType = ruleType;
        this.configJson = configJson;
        this.operator = operator;
    }

    public UUID getId() { return id; }
    public UUID getTierId() { return tierId; }
    public String getRuleType() { return ruleType; }
    public String getConfigJson() { return configJson; }

    /** "AND" means all AND-grouped rules must pass; "OR" means any OR-grouped rule suffices. */
    public String getOperator() { return operator; }
}
