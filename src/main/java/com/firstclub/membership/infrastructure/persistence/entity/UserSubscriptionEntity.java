package com.firstclub.membership.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_subscriptions")
public class UserSubscriptionEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "plan_id", nullable = false, columnDefinition = "uuid")
    private UUID planId;

    @Column(name = "tier_id", nullable = false, columnDefinition = "uuid")
    private UUID tierId;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date", nullable = false)
    private Instant endDate;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "previous_tier_id", columnDefinition = "uuid")
    private UUID previousTierId;

    @Column(name = "scheduled_tier_id", columnDefinition = "uuid")
    private UUID scheduledTierId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public UUID getTierId() { return tierId; }
    public void setTierId(UUID tierId) { this.tierId = tierId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }
    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }
    public boolean isAutoRenew() { return autoRenew; }
    public void setAutoRenew(boolean autoRenew) { this.autoRenew = autoRenew; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public UUID getPreviousTierId() { return previousTierId; }
    public void setPreviousTierId(UUID previousTierId) { this.previousTierId = previousTierId; }
    public UUID getScheduledTierId() { return scheduledTierId; }
    public void setScheduledTierId(UUID scheduledTierId) { this.scheduledTierId = scheduledTierId; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
