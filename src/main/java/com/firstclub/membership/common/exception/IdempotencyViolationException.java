package com.firstclub.membership.common.exception;

public class IdempotencyViolationException extends DomainException {

    public IdempotencyViolationException(String message) {
        super(message);
    }
}
