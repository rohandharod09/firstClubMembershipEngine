package com.firstclub.membership.domain.statemachine;

import com.firstclub.membership.common.exception.IllegalStateTransitionException;
import com.firstclub.membership.domain.model.SubscriptionStatus;

import java.util.Map;
import java.util.Set;

/**
 * Pure domain state machine for subscription lifecycle transitions.
 * No Spring or framework dependencies. All business rules about valid
 * state transitions live here.
 */
public final class SubscriptionStateMachine {

    private static final Map<SubscriptionStatus, Set<SubscriptionStatus>> VALID_TRANSITIONS =
            Map.of(
                    SubscriptionStatus.PENDING_PAYMENT,
                    Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAYMENT_FAILED),

                    SubscriptionStatus.PAYMENT_FAILED,
                    Set.of(SubscriptionStatus.PENDING_PAYMENT),

                    SubscriptionStatus.ACTIVE,
                    Set.of(
                            SubscriptionStatus.UPGRADE_PENDING,
                            SubscriptionStatus.DOWNGRADE_SCHEDULED,
                            SubscriptionStatus.CANCELLED,
                            SubscriptionStatus.GRACE_PERIOD,
                            SubscriptionStatus.EXPIRED
                    ),

                    SubscriptionStatus.UPGRADE_PENDING,
                    Set.of(SubscriptionStatus.ACTIVE),

                    SubscriptionStatus.DOWNGRADE_SCHEDULED,
                    Set.of(SubscriptionStatus.ACTIVE),

                    SubscriptionStatus.GRACE_PERIOD,
                    Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED),

                    SubscriptionStatus.CANCELLED,
                    Set.of(),

                    SubscriptionStatus.EXPIRED,
                    Set.of()
            );

    private SubscriptionStateMachine() {}

    public static void validateTransition(SubscriptionStatus from, SubscriptionStatus to) {
        Set<SubscriptionStatus> allowedTargets = VALID_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowedTargets.contains(to)) {
            throw new IllegalStateTransitionException(from.name(), to.name());
        }
    }

    public static boolean canTransition(SubscriptionStatus from, SubscriptionStatus to) {
        return VALID_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
