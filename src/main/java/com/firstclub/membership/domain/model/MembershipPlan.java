package com.firstclub.membership.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MembershipPlan {

    private final UUID id;
    private final String name;
    private final int durationDays;
    private final long basePriceCents;
    private final String currency;
    private final boolean active;
    private final List<MembershipTier> tiers;

    // @JsonCreator required so Jackson can reconstruct this object when reading from Redis.
    // Without it, Jackson has no no-arg constructor and no property-based creator, so
    // cache reads silently fail and every call falls back to the DB.
    @JsonCreator
    public MembershipPlan(
            @JsonProperty("id") UUID id,
            @JsonProperty("name") String name,
            @JsonProperty("durationDays") int durationDays,
            @JsonProperty("basePriceCents") long basePriceCents,
            @JsonProperty("currency") String currency,
            @JsonProperty("active") boolean active,
            @JsonProperty("tiers") List<MembershipTier> tiers) {
        this.id = id;
        this.name = name;
        this.durationDays = durationDays;
        this.basePriceCents = basePriceCents;
        this.currency = currency;
        this.active = active;
        // Use ArrayList (not List.copyOf / List.of) — those return internal JDK classes
        // (ImmutableCollections$ListN) that Jackson cannot reconstruct from Redis JSON.
        this.tiers = tiers != null ? new ArrayList<>(tiers) : new ArrayList<>();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public int getDurationDays() { return durationDays; }
    public long getBasePriceCents() { return basePriceCents; }
    public String getCurrency() { return currency; }
    public boolean isActive() { return active; }
    public List<MembershipTier> getTiers() { return tiers; }
}
