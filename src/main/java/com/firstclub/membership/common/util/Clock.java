package com.firstclub.membership.common.util;

import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Injectable clock abstraction for testability.
 * In tests, replace this bean with a fixed-time implementation.
 */
@Component
public class Clock {

    public Instant now() {
        return Instant.now();
    }
}
