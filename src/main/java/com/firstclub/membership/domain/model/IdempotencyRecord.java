package com.firstclub.membership.domain.model;

import java.time.Instant;
import java.util.UUID;

public class IdempotencyRecord {

    private final UUID id;
    private final String idempotencyKey;
    private final String resourceType;
    private final UUID resourceId;
    private final String responsePayload;
    private final Instant createdAt;
    private final Instant expiresAt;

    public IdempotencyRecord(UUID id, String idempotencyKey, String resourceType,
                             UUID resourceId, String responsePayload,
                             Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.responsePayload = responsePayload;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public String getResponsePayload() { return responsePayload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
