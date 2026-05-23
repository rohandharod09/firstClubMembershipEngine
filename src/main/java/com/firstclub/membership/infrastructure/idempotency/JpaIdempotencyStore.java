package com.firstclub.membership.infrastructure.idempotency;

import com.firstclub.membership.application.port.outbound.IdempotencyStore;
import com.firstclub.membership.domain.model.IdempotencyRecord;
import com.firstclub.membership.infrastructure.persistence.entity.IdempotencyRecordEntity;
import com.firstclub.membership.infrastructure.persistence.repository.IdempotencyRecordJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaIdempotencyStore implements IdempotencyStore {

    private final IdempotencyRecordJpaRepository jpaRepo;
    private final long ttlHours;

    public JpaIdempotencyStore(IdempotencyRecordJpaRepository jpaRepo,
                               @Value("${membership.idempotency-ttl-hours:24}") long ttlHours) {
        this.jpaRepo = jpaRepo;
        this.ttlHours = ttlHours;
    }

    @Override
    public Optional<IdempotencyRecord> findByKey(String idempotencyKey) {
        return jpaRepo.findByIdempotencyKey(idempotencyKey)
                .map(this::toDomain)
                .filter(record -> !record.isExpired());
    }

    @Override
    public void save(String idempotencyKey, String resourceType, UUID resourceId,
                     String responsePayload) {
        IdempotencyRecordEntity entity = new IdempotencyRecordEntity();
        entity.setId(UUID.randomUUID());
        entity.setIdempotencyKey(idempotencyKey);
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setResponsePayload(responsePayload);
        entity.setCreatedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plusSeconds(ttlHours * 3600));
        jpaRepo.save(entity);
    }

    private IdempotencyRecord toDomain(IdempotencyRecordEntity entity) {
        return new IdempotencyRecord(
                entity.getId(), entity.getIdempotencyKey(), entity.getResourceType(),
                entity.getResourceId(), entity.getResponsePayload(),
                entity.getCreatedAt(), entity.getExpiresAt());
    }
}
