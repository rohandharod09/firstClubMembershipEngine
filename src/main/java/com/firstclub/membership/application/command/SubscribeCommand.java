package com.firstclub.membership.application.command;

import java.util.UUID;

public record SubscribeCommand(
        UUID userId,
        UUID planId,
        UUID tierId,
        boolean autoRenew,
        String userCohort,
        int orderCount,
        long totalOrderValueCents,
        String idempotencyKey
) {}
