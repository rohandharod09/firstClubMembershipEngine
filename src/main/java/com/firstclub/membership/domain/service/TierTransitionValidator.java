package com.firstclub.membership.domain.service;

import com.firstclub.membership.common.exception.SubscriptionConflictException;
import com.firstclub.membership.domain.model.MembershipTier;

/**
 * Validates tier-to-tier transitions based on rank ordering.
 * Upgrade = higher rank. Downgrade = lower rank. Same rank = invalid.
 */
public class TierTransitionValidator {

    private TierTransitionValidator() {}

    public static void validateUpgrade(MembershipTier currentTier, MembershipTier targetTier) {
        if (!targetTier.isHigherRankThan(currentTier)) {
            throw new SubscriptionConflictException(
                    String.format("Tier '%s' (rank %d) is not an upgrade over '%s' (rank %d). " +
                                    "Target tier must have a higher rank.",
                            targetTier.getName(), targetTier.getRank(),
                            currentTier.getName(), currentTier.getRank()));
        }
        if (!currentTier.getPlanId().equals(targetTier.getPlanId())) {
            throw new SubscriptionConflictException(
                    "Tier change must be within the same plan. " +
                    "To change plans, cancel and re-subscribe.");
        }
    }

    public static void validateDowngrade(MembershipTier currentTier, MembershipTier targetTier) {
        if (!targetTier.isLowerRankThan(currentTier)) {
            throw new SubscriptionConflictException(
                    String.format("Tier '%s' (rank %d) is not a downgrade from '%s' (rank %d). " +
                                    "Target tier must have a lower rank.",
                            targetTier.getName(), targetTier.getRank(),
                            currentTier.getName(), currentTier.getRank()));
        }
        if (!currentTier.getPlanId().equals(targetTier.getPlanId())) {
            throw new SubscriptionConflictException(
                    "Tier change must be within the same plan.");
        }
    }
}
