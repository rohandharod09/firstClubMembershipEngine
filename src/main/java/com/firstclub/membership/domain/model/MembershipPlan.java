package com.firstclub.membership.domain.model;

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

    public MembershipPlan(UUID id, String name, int durationDays, long basePriceCents,
                          String currency, boolean active, List<MembershipTier> tiers) {
        this.id = id;
        this.name = name;
        this.durationDays = durationDays;
        this.basePriceCents = basePriceCents;
        this.currency = currency;
        this.active = active;
        this.tiers = tiers != null ? List.copyOf(tiers) : List.of();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public int getDurationDays() { return durationDays; }
    public long getBasePriceCents() { return basePriceCents; }
    public String getCurrency() { return currency; }
    public boolean isActive() { return active; }
    public List<MembershipTier> getTiers() { return tiers; }
}
