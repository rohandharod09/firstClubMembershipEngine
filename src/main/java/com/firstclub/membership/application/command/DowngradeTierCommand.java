package com.firstclub.membership.application.command;

import java.util.UUID;

public record DowngradeTierCommand(
        UUID subscriptionId,
        UUID userId,
        UUID targetTierId,
        String idempotencyKey
) {}
