package com.firstclub.membership.api.rest.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CancelRequest(
        @NotNull(message = "userId is required") UUID userId,
        String reason,
        @NotNull(message = "idempotencyKey is required") String idempotencyKey
) {}
