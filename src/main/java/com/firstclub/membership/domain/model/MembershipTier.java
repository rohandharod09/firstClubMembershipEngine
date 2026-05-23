package com.firstclub.membership.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MembershipTier {

    private final UUID id;
    private final UUID planId;
    private final String name;
    private final int rank;
    private final long priceCents;
    private final boolean active;
    private final List<TierBenefit> benefits;
    private final List<TierEligibilityRule> eligibilityRules;

    @JsonCreator
    public MembershipTier(
            @JsonProperty("id") UUID id,
            @JsonProperty("planId") UUID planId,
            @JsonProperty("name") String name,
            @JsonProperty("rank") int rank,
            @JsonProperty("priceCents") long priceCents,
            @JsonProperty("active") boolean active,
            @JsonProperty("benefits") List<TierBenefit> benefits,
            @JsonProperty("eligibilityRules") List<TierEligibilityRule> eligibilityRules) {
        this.id = id;
        this.planId = planId;
        this.name = name;
        this.rank = rank;
        this.priceCents = priceCents;
        this.active = active;
        this.benefits = benefits != null ? new ArrayList<>(benefits) : new ArrayList<>();
        this.eligibilityRules = eligibilityRules != null ? new ArrayList<>(eligibilityRules) : new ArrayList<>();
    }

    public UUID getId() { return id; }
    public UUID getPlanId() { return planId; }
    public String getName() { return name; }
    public int getRank() { return rank; }
    public long getPriceCents() { return priceCents; }
    public boolean isActive() { return active; }
    public List<TierBenefit> getBenefits() { return benefits; }
    public List<TierEligibilityRule> getEligibilityRules() { return eligibilityRules; }

    public boolean isHigherRankThan(MembershipTier other) {
        return this.rank > other.rank;
    }

    public boolean isLowerRankThan(MembershipTier other) {
        return this.rank < other.rank;
    }
}
