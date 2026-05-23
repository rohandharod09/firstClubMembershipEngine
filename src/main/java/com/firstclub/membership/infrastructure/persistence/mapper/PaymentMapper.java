package com.firstclub.membership.infrastructure.persistence.mapper;

import com.firstclub.membership.domain.model.PaymentStatus;
import com.firstclub.membership.domain.model.PaymentTransaction;
import com.firstclub.membership.infrastructure.persistence.entity.PaymentTransactionEntity;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentTransactionEntity toEntity(PaymentTransaction domain) {
        PaymentTransactionEntity entity = new PaymentTransactionEntity();
        entity.setId(domain.getId());
        entity.setSubscriptionId(domain.getSubscriptionId());
        entity.setUserId(domain.getUserId());
        entity.setAmountCents(domain.getAmountCents());
        entity.setCurrency(domain.getCurrency());
        entity.setStatus(domain.getStatus().name());
        entity.setExternalTxnId(domain.getExternalTxnId());
        entity.setIdempotencyKey(domain.getIdempotencyKey());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }

    public PaymentTransaction toDomain(PaymentTransactionEntity entity) {
        return new PaymentTransaction(
                entity.getId(), entity.getSubscriptionId(), entity.getUserId(),
                entity.getAmountCents(), entity.getCurrency(),
                PaymentStatus.valueOf(entity.getStatus()),
                entity.getExternalTxnId(), entity.getIdempotencyKey(), entity.getCreatedAt());
    }
}
