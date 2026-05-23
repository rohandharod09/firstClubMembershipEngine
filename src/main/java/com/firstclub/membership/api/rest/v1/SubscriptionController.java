package com.firstclub.membership.api.rest.v1;

import com.firstclub.membership.api.rest.dto.request.*;
import com.firstclub.membership.api.rest.dto.response.SubscriptionResponse;
import com.firstclub.membership.api.rest.mapper.SubscriptionResponseMapper;
import com.firstclub.membership.application.command.*;
import com.firstclub.membership.application.handler.PlanQueryHandler;
import com.firstclub.membership.application.handler.SubscriptionCommandHandler;
import com.firstclub.membership.application.query.GetActiveSubscriptionQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@Tag(name = "Subscriptions", description = "Membership subscription lifecycle APIs")
public class SubscriptionController {

    private final SubscriptionCommandHandler commandHandler;
    private final PlanQueryHandler queryHandler;
    private final SubscriptionResponseMapper mapper;

    public SubscriptionController(SubscriptionCommandHandler commandHandler,
                                   PlanQueryHandler queryHandler,
                                   SubscriptionResponseMapper mapper) {
        this.commandHandler = commandHandler;
        this.queryHandler = queryHandler;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Subscribe to a membership plan and tier")
    public ResponseEntity<SubscriptionResponse> subscribe(
            @Valid @RequestBody SubscribeRequest request) {
        var command = new SubscribeCommand(
                request.userId(), request.planId(), request.tierId(),
                request.autoRenew(), request.userCohort(),
                request.orderCount(), request.totalOrderValueCents(),
                request.idempotencyKey());
        var subscription = commandHandler.subscribe(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(subscription));
    }

    @GetMapping("/me")
    @Operation(summary = "Get active subscription for a user")
    public ResponseEntity<SubscriptionResponse> getActiveSubscription(
            @RequestParam UUID userId) {
        var subscription = queryHandler.getActiveSubscription(
                new GetActiveSubscriptionQuery(userId));
        return ResponseEntity.ok(mapper.toResponse(subscription));
    }

    @PostMapping("/{id}/upgrade")
    @Operation(summary = "Upgrade to a higher tier (immediate, with prorated charge)")
    public ResponseEntity<SubscriptionResponse> upgrade(
            @PathVariable UUID id,
            @Valid @RequestBody TierChangeRequest request,
            @RequestParam UUID userId) {
        var command = new UpgradeTierCommand(
                id, userId, request.targetTierId(),
                request.userCohort(), request.orderCount(), request.totalOrderValueCents(),
                request.idempotencyKey());
        var subscription = commandHandler.upgrade(command);
        return ResponseEntity.ok(mapper.toResponse(subscription));
    }

    @PostMapping("/{id}/downgrade")
    @Operation(summary = "Schedule a downgrade to a lower tier (effective at period end)")
    public ResponseEntity<SubscriptionResponse> downgrade(
            @PathVariable UUID id,
            @Valid @RequestBody TierChangeRequest request,
            @RequestParam UUID userId) {
        var command = new DowngradeTierCommand(
                id, userId, request.targetTierId(), request.idempotencyKey());
        var subscription = commandHandler.downgrade(command);
        return ResponseEntity.ok(mapper.toResponse(subscription));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a subscription (benefits remain until end date)")
    public ResponseEntity<SubscriptionResponse> cancel(
            @PathVariable UUID id,
            @Valid @RequestBody CancelRequest request) {
        var command = new CancelSubscriptionCommand(
                id, request.userId(), request.reason(), request.idempotencyKey());
        var subscription = commandHandler.cancel(command);
        return ResponseEntity.ok(mapper.toResponse(subscription));
    }
}
