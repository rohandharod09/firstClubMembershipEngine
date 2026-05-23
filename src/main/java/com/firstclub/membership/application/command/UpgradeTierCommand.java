package com.firstclub.membership.application.command;

import java.util.UUID;

public record UpgradeTierCommand(
        UUID subscriptionId,
        UUID userId,
        UUID targetTierId,
        String userCohort,
        int orderCount,
        long totalOrderValueCents,
        String idempotencyKey
) {}
