package com.firstclub.membership.common.exception;

public class SubscriptionConflictException extends DomainException {

    public SubscriptionConflictException(String message) {
        super(message);
    }
}
