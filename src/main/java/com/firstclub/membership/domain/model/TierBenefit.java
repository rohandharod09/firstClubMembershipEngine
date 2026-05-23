package com.firstclub.membership.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class TierBenefit {

    private final UUID id;
    private final UUID tierId;
    private final BenefitType benefitType;
    private final String configJson;
    private final boolean active;

    @JsonCreator
    public TierBenefit(
            @JsonProperty("id") UUID id,
            @JsonProperty("tierId") UUID tierId,
            @JsonProperty("benefitType") BenefitType benefitType,
            @JsonProperty("configJson") String configJson,
            @JsonProperty("active") boolean active) {
        this.id = id;
        this.tierId = tierId;
        this.benefitType = benefitType;
        this.configJson = configJson;
        this.active = active;
    }

    public UUID getId() { return id; }
    public UUID getTierId() { return tierId; }
    public BenefitType getBenefitType() { return benefitType; }
    public String getConfigJson() { return configJson; }
    public boolean isActive() { return active; }
}
