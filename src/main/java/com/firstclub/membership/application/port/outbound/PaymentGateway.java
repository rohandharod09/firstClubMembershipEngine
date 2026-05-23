package com.firstclub.membership.application.port.outbound;

import java.util.UUID;

public interface PaymentGateway {

    record PaymentResult(boolean success, String externalTxnId, String failureReason) {}

    PaymentResult charge(UUID userId, long amountCents, String currency, String idempotencyKey);
}
