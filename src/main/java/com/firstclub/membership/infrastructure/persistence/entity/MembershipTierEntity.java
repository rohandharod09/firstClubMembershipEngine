package com.firstclub.membership.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "membership_tiers")
public class MembershipTierEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private MembershipPlanEntity plan;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "rank", nullable = false)
    private int rank;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "tier", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<TierBenefitEntity> benefits = new ArrayList<>();

    @OneToMany(mappedBy = "tier", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<TierEligibilityRuleEntity> eligibilityRules = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public MembershipPlanEntity getPlan() { return plan; }
    public void setPlan(MembershipPlanEntity plan) { this.plan = plan; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public long getPriceCents() { return priceCents; }
    public void setPriceCents(long priceCents) { this.priceCents = priceCents; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<TierBenefitEntity> getBenefits() { return benefits; }
    public void setBenefits(List<TierBenefitEntity> benefits) { this.benefits = benefits; }
    public List<TierEligibilityRuleEntity> getEligibilityRules() { return eligibilityRules; }
    public void setEligibilityRules(List<TierEligibilityRuleEntity> eligibilityRules) {
        this.eligibilityRules = eligibilityRules;
    }
}
