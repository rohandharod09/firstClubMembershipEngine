package com.firstclub.membership.domain.benefit;

import com.firstclub.membership.domain.model.BenefitType;

public class BenefitResult {

    private final BenefitType benefitType;
    private final boolean applied;
    private final long discountCents;
    private final boolean freeDelivery;
    private final String description;

    private BenefitResult(BenefitType benefitType, boolean applied, long discountCents,
                          boolean freeDelivery, String description) {
        this.benefitType = benefitType;
        this.applied = applied;
        this.discountCents = discountCents;
        this.freeDelivery = freeDelivery;
        this.description = description;
    }

    public static BenefitResult applied(BenefitType type, long discountCents,
                                        boolean freeDelivery, String description) {
        return new BenefitResult(type, true, discountCents, freeDelivery, description);
    }

    public static BenefitResult notApplied(BenefitType type, String reason) {
        return new BenefitResult(type, false, 0, false, reason);
    }

    public BenefitType getBenefitType() { return benefitType; }
    public boolean isApplied() { return applied; }
    public long getDiscountCents() { return discountCents; }
    public boolean isFreeDelivery() { return freeDelivery; }
    public String getDescription() { return description; }
}
