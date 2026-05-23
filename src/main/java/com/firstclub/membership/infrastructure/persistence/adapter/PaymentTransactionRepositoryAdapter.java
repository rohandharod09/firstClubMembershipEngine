package com.firstclub.membership.infrastructure.persistence.adapter;

import com.firstclub.membership.application.port.outbound.PaymentTransactionRepository;
import com.firstclub.membership.domain.model.PaymentTransaction;
import com.firstclub.membership.infrastructure.persistence.mapper.PaymentMapper;
import com.firstclub.membership.infrastructure.persistence.repository.PaymentTransactionJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PaymentTransactionRepositoryAdapter implements PaymentTransactionRepository {

    private final PaymentTransactionJpaRepository jpaRepo;
    private final PaymentMapper mapper;

    public PaymentTransactionRepositoryAdapter(PaymentTransactionJpaRepository jpaRepo,
                                                PaymentMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    @Override
    public PaymentTransaction save(PaymentTransaction transaction) {
        var entity = mapper.toEntity(transaction);
        var saved = jpaRepo.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepo.findByIdempotencyKey(idempotencyKey).map(mapper::toDomain);
    }
}
