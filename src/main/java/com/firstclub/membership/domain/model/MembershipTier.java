package com.firstclub.membership.domain.model;

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

    public MembershipTier(UUID id, UUID planId, String name, int rank, long priceCents,
                          boolean active, List<TierBenefit> benefits,
                          List<TierEligibilityRule> eligibilityRules) {
        this.id = id;
        this.planId = planId;
        this.name = name;
        this.rank = rank;
        this.priceCents = priceCents;
        this.active = active;
        this.benefits = benefits != null ? List.copyOf(benefits) : List.of();
        this.eligibilityRules = eligibilityRules != null ? List.copyOf(eligibilityRules) : List.of();
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
