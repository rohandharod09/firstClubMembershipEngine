package com.firstclub.membership.common.exception;

public class PaymentFailedException extends DomainException {

    public PaymentFailedException(String message) {
        super(message);
    }
}
