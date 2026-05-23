package com.firstclub.membership.common.exception;

public class IllegalStateTransitionException extends DomainException {

    public IllegalStateTransitionException(String from, String to) {
        super(String.format("Invalid subscription state transition: %s -> %s", from, to));
    }
}
