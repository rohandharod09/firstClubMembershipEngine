package com.firstclub.membership.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.firstclub.membership.common.exception.IllegalStateTransitionException;
import com.firstclub.membership.domain.statemachine.SubscriptionStateMachine;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root for a user's subscription.
 * All state mutations go through this class, validated by the state machine.
 */
public class UserSubscription {

    private UUID id;
    private UUID userId;
    private UUID planId;
    private UUID tierId;
    private SubscriptionStatus status;
    private Instant startDate;
    private Instant endDate;
    private boolean autoRenew;
    private Instant cancelledAt;
    private UUID previousTierId;
    private UUID scheduledTierId;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    // @JsonCreator on the no-arg constructor lets Jackson create an empty instance
    // and then populate it via the existing public setters during Redis deserialization.
    @JsonCreator
    private UserSubscription() {}

    /** Reconstructs an aggregate loaded from persistence (mapper use only). */
    public static UserSubscription reconstitute() {
        return new UserSubscription();
    }

    public static UserSubscription create(UUID userId, UUID planId, UUID tierId,
                                          Instant startDate, Instant endDate, boolean autoRenew) {
        UserSubscription sub = new UserSubscription();
        sub.id = UUID.randomUUID();
        sub.userId = userId;
        sub.planId = planId;
        sub.tierId = tierId;
        sub.status = SubscriptionStatus.PENDING_PAYMENT;
        sub.startDate = startDate;
        sub.endDate = endDate;
        sub.autoRenew = autoRenew;
        sub.version = 0;
        sub.createdAt = Instant.now();
        sub.updatedAt = Instant.now();
        return sub;
    }

    public void activate() {
        transitionTo(SubscriptionStatus.ACTIVE);
    }

    public void markPaymentFailed() {
        transitionTo(SubscriptionStatus.PAYMENT_FAILED);
    }

    public void retryPayment() {
        transitionTo(SubscriptionStatus.PENDING_PAYMENT);
    }

    public void startUpgrade(UUID newTierId) {
        this.previousTierId = this.tierId;
        this.scheduledTierId = newTierId;
        transitionTo(SubscriptionStatus.UPGRADE_PENDING);
    }

    public void completeUpgrade() {
        if (scheduledTierId != null) {
            this.tierId = scheduledTierId;
            this.scheduledTierId = null;
        }
        transitionTo(SubscriptionStatus.ACTIVE);
    }

    public void rollbackUpgrade() {
        this.scheduledTierId = null;
        transitionTo(SubscriptionStatus.ACTIVE);
    }

    public void scheduleDowngrade(UUID targetTierId) {
        this.previousTierId = this.tierId;
        this.scheduledTierId = targetTierId;
        transitionTo(SubscriptionStatus.DOWNGRADE_SCHEDULED);
    }

    public void applyScheduledDowngrade() {
        if (scheduledTierId != null) {
            this.tierId = scheduledTierId;
            this.scheduledTierId = null;
        }
        transitionTo(SubscriptionStatus.ACTIVE);
    }

    public void cancel() {
        this.cancelledAt = Instant.now();
        transitionTo(SubscriptionStatus.CANCELLED);
    }

    public void enterGracePeriod() {
        transitionTo(SubscriptionStatus.GRACE_PERIOD);
    }

    public void expire() {
        transitionTo(SubscriptionStatus.EXPIRED);
    }

    public void renew(int durationDays) {
        this.endDate = this.endDate.plusSeconds((long) durationDays * 86400);
        transitionTo(SubscriptionStatus.ACTIVE);
    }

    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE
                || status == SubscriptionStatus.UPGRADE_PENDING
                || status == SubscriptionStatus.DOWNGRADE_SCHEDULED;
    }

    public boolean isExpiredAt(Instant checkTime) {
        return endDate != null && endDate.isBefore(checkTime);
    }

    private void transitionTo(SubscriptionStatus newStatus) {
        SubscriptionStateMachine.validateTransition(this.status, newStatus);
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getPlanId() { return planId; }
    public UUID getTierId() { return tierId; }
    public SubscriptionStatus getStatus() { return status; }
    public Instant getStartDate() { return startDate; }
    public Instant getEndDate() { return endDate; }
    public boolean isAutoRenew() { return autoRenew; }
    public Instant getCancelledAt() { return cancelledAt; }
    public UUID getPreviousTierId() { return previousTierId; }
    public UUID getScheduledTierId() { return scheduledTierId; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setAutoRenew(boolean autoRenew) { this.autoRenew = autoRenew; }

    // Used by the infrastructure mapper to reconstruct from JPA entity
    public void setId(UUID id) { this.id = id; }
    public void setVersion(long version) { this.version = version; }
    public void setStatus(SubscriptionStatus status) { this.status = status; }
    public void setTierId(UUID tierId) { this.tierId = tierId; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }
    public void setScheduledTierId(UUID scheduledTierId) { this.scheduledTierId = scheduledTierId; }
    public void setPreviousTierId(UUID previousTierId) { this.previousTierId = previousTierId; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }
}
