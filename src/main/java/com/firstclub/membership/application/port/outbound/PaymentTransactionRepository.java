package com.firstclub.membership.application.port.outbound;

import com.firstclub.membership.domain.model.PaymentTransaction;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository {

    PaymentTransaction save(PaymentTransaction transaction);

    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);
}
