package com.firstclub.membership.infrastructure.payment;

import com.firstclub.membership.application.port.outbound.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Mock payment gateway for demo and testing.
 * Always succeeds unless amount is 0.
 * Replace with Razorpay/Stripe adapter in production.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    @Override
    public PaymentResult charge(UUID userId, long amountCents, String currency,
                                String idempotencyKey) {
        log.info("PAYMENT charge: userId={} amount={}cents currency={} idempotencyKey={}",
                userId, amountCents, currency, idempotencyKey);

        if (amountCents < 0) {
            return new PaymentResult(false, null, "Invalid amount: " + amountCents);
        }

        String externalTxnId = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("PAYMENT success: externalTxnId={}", externalTxnId);
        return new PaymentResult(true, externalTxnId, null);
    }
}
