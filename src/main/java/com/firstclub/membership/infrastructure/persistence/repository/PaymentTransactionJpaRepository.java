package com.firstclub.membership.infrastructure.persistence.repository;

import com.firstclub.membership.infrastructure.persistence.entity.PaymentTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionJpaRepository extends JpaRepository<PaymentTransactionEntity, UUID> {

    Optional<PaymentTransactionEntity> findByIdempotencyKey(String idempotencyKey);
}
