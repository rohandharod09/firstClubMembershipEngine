package com.firstclub.membership.domain;

import com.firstclub.membership.common.exception.IllegalStateTransitionException;
import com.firstclub.membership.domain.model.SubscriptionStatus;
import com.firstclub.membership.domain.statemachine.SubscriptionStateMachine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SubscriptionStateMachine")
class SubscriptionStateMachineTest {

    @Test
    @DisplayName("PENDING_PAYMENT -> ACTIVE is valid")
    void pendingToActive() {
        assertThatNoException().isThrownBy(() ->
                SubscriptionStateMachine.validateTransition(
                        SubscriptionStatus.PENDING_PAYMENT, SubscriptionStatus.ACTIVE));
    }

    @Test
    @DisplayName("PENDING_PAYMENT -> PAYMENT_FAILED is valid")
    void pendingToFailed() {
        assertThatNoException().isThrownBy(() ->
                SubscriptionStateMachine.validateTransition(
                        SubscriptionStatus.PENDING_PAYMENT, SubscriptionStatus.PAYMENT_FAILED));
    }

    @Test
    @DisplayName("ACTIVE -> UPGRADE_PENDING is valid")
    void activeToUpgradePending() {
        assertThatNoException().isThrownBy(() ->
                SubscriptionStateMachine.validateTransition(
                        SubscriptionStatus.ACTIVE, SubscriptionStatus.UPGRADE_PENDING));
    }

    @Test
    @DisplayName("ACTIVE -> DOWNGRADE_SCHEDULED is valid")
    void activeToDowngradeScheduled() {
        assertThatNoException().isThrownBy(() ->
                SubscriptionStateMachine.validateTransition(
                        SubscriptionStatus.ACTIVE, SubscriptionStatus.DOWNGRADE_SCHEDULED));
    }

    @Test
    @DisplayName("ACTIVE -> CANCELLED is valid")
    void activeToCancelled() {
        assertThatNoException().isThrownBy(() ->
                SubscriptionStateMachine.validateTransition(
                        SubscriptionStatus.ACTIVE, SubscriptionStatus.CANCELLED));
    }

    @Test
    @DisplayName("ACTIVE -> GRACE_PERIOD is valid")
    void activeToGracePeriod() {
        assertThatNoException().isThrownBy(() ->
                SubscriptionStateMachine.validateTransition(
                        SubscriptionStatus.ACTIVE, SubscriptionStatus.GRACE_PERIOD));
    }

    @Test
    @DisplayName("GRACE_PERIOD -> ACTIVE is valid (renewal)")
    void gracePeriodToActive() {
        assertThatNoException().isThrownBy(() ->
                SubscriptionStateMachine.validateTransition(
                        SubscriptionStatus.GRACE_PERIOD, SubscriptionStatus.ACTIVE));
    }

    @Test
    @DisplayName("GRACE_PERIOD -> EXPIRED is valid")
    void gracePeriodToExpired() {
        assertThatNoException().isThrownBy(() ->
                SubscriptionStateMachine.validateTransition(
                        SubscriptionStatus.GRACE_PERIOD, SubscriptionStatus.EXPIRED));
    }

    @Test
    @DisplayName("CANCELLED -> ACTIVE is invalid")
    void cancelledToActiveIsInvalid() {
        assertThatThrownBy(() ->
                SubscriptionStateMachine.validateTransition(
                        SubscriptionStatus.CANCELLED, SubscriptionStatus.ACTIVE))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("CANCELLED")
                .hasMessageContaining("ACTIVE");
    }

    @Test
    @DisplayName("EXPIRED -> ACTIVE is invalid")
    void expiredToActiveIsInvalid() {
        assertThatThrownBy(() ->
                SubscriptionStateMachine.validateTransition(
                        SubscriptionStatus.EXPIRED, SubscriptionStatus.ACTIVE))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    @DisplayName("ACTIVE -> PENDING_PAYMENT is invalid")
    void activeToPendingIsInvalid() {
        assertThatThrownBy(() ->
                SubscriptionStateMachine.validateTransition(
                        SubscriptionStatus.ACTIVE, SubscriptionStatus.PENDING_PAYMENT))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    @DisplayName("canTransition returns correct boolean")
    void canTransitionCheck() {
        assertThat(SubscriptionStateMachine.canTransition(
                SubscriptionStatus.ACTIVE, SubscriptionStatus.CANCELLED)).isTrue();
        assertThat(SubscriptionStateMachine.canTransition(
                SubscriptionStatus.CANCELLED, SubscriptionStatus.ACTIVE)).isFalse();
    }
}
