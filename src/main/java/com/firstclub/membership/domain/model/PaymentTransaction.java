package com.firstclub.membership.domain.model;

import java.time.Instant;
import java.util.UUID;

public class PaymentTransaction {

    private final UUID id;
    private final UUID subscriptionId;
    private final UUID userId;
    private final long amountCents;
    private final String currency;
    private PaymentStatus status;
    private String externalTxnId;          // set after gateway responds
    private final String idempotencyKey;
    private final Instant createdAt;

    public PaymentTransaction(UUID id, UUID subscriptionId, UUID userId, long amountCents,
                              String currency, PaymentStatus status, String externalTxnId,
                              String idempotencyKey, Instant createdAt) {
        this.id = id;
        this.subscriptionId = subscriptionId;
        this.userId = userId;
        this.amountCents = amountCents;
        this.currency = currency;
        this.status = status;
        this.externalTxnId = externalTxnId;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public static PaymentTransaction create(UUID subscriptionId, UUID userId, long amountCents,
                                            String currency, String idempotencyKey) {
        return new PaymentTransaction(
                UUID.randomUUID(), subscriptionId, userId, amountCents,
                currency, PaymentStatus.PENDING, null, idempotencyKey, Instant.now()
        );
    }

    public void markSuccess(String externalTxnId) {
        this.status = PaymentStatus.SUCCESS;
        this.externalTxnId = externalTxnId;
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public UUID getId() { return id; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public UUID getUserId() { return userId; }
    public long getAmountCents() { return amountCents; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getExternalTxnId() { return externalTxnId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
}
