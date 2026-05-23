package com.firstclub.membership.api.rest.v1;

import com.firstclub.membership.api.rest.dto.response.PlansListResponse;
import com.firstclub.membership.api.rest.mapper.SubscriptionResponseMapper;
import com.firstclub.membership.application.handler.PlanQueryHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "Plans", description = "Membership plan catalog APIs")
public class PlanController {

    private final PlanQueryHandler queryHandler;
    private final SubscriptionResponseMapper mapper;

    public PlanController(PlanQueryHandler queryHandler, SubscriptionResponseMapper mapper) {
        this.queryHandler = queryHandler;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Get all active membership plans with tiers and benefits")
    public ResponseEntity<PlansListResponse> getAllPlans() {
        var plans = queryHandler.getAllActivePlans().stream()
                .map(mapper::toPlanResponse)
                .toList();
        return ResponseEntity.ok(new PlansListResponse(plans));
    }
}
