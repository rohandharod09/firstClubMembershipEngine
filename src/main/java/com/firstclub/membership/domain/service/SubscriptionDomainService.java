package com.firstclub.membership.domain.service;

import com.firstclub.membership.domain.model.MembershipPlan;
import com.firstclub.membership.domain.model.MembershipTier;
import com.firstclub.membership.domain.model.UserSubscription;

import java.time.Instant;
import java.util.UUID;

/**
 * Pure domain service that encapsulates business logic for creating
 * and mutating subscriptions. No persistence or framework dependencies.
 */
public class SubscriptionDomainService {

    private SubscriptionDomainService() {}

    public static UserSubscription createNew(UUID userId, MembershipPlan plan,
                                             MembershipTier tier, Instant now,
                                             boolean autoRenew) {
        Instant endDate = now.plusSeconds((long) plan.getDurationDays() * 86400);
        return UserSubscription.create(userId, plan.getId(), tier.getId(), now, endDate, autoRenew);
    }

    /**
     * Calculate prorated upgrade cost.
     * Cost = (daysRemaining / totalDays) * (newTierPrice - currentTierPrice)
     */
    public static long calculateUpgradeCost(UserSubscription subscription,
                                            MembershipTier currentTier,
                                            MembershipTier targetTier,
                                            Instant now) {
        long totalDurationSeconds = subscription.getEndDate().getEpochSecond()
                - subscription.getStartDate().getEpochSecond();
        long remainingSeconds = subscription.getEndDate().getEpochSecond() - now.getEpochSecond();

        if (remainingSeconds <= 0 || totalDurationSeconds <= 0) {
            return 0;
        }

        long priceDifference = targetTier.getPriceCents() - currentTier.getPriceCents();
        if (priceDifference <= 0) {
            return 0;
        }

        return (priceDifference * remainingSeconds) / totalDurationSeconds;
    }
}
