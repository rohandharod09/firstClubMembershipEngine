package com.firstclub.membership.application.port.outbound;

import com.firstclub.membership.domain.model.IdempotencyRecord;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyStore {

    Optional<IdempotencyRecord> findByKey(String idempotencyKey);

    void save(String idempotencyKey, String resourceType, UUID resourceId, String responsePayload);
}
