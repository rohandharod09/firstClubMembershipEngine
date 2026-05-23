package com.firstclub.membership.api.rest.v1.admin;

import com.firstclub.membership.api.rest.dto.request.*;
import com.firstclub.membership.api.rest.dto.response.*;
import com.firstclub.membership.application.admin.AdminCatalogService;
import com.firstclub.membership.application.admin.TierProgressService;
import com.firstclub.membership.infrastructure.persistence.entity.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin APIs for managing the membership catalog.
 * In production: secure with RBAC (admin role only). For demo: open.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Catalog management APIs — create plans, tiers, benefits, rules")
public class AdminController {

    private final AdminCatalogService catalogService;
    private final TierProgressService progressService;

    public AdminController(AdminCatalogService catalogService,
                            TierProgressService progressService) {
        this.catalogService = catalogService;
        this.progressService = progressService;
    }

    // =========================================================================
    // PLANS
    // =========================================================================

    @GetMapping("/plans")
    @Operation(summary = "List all plans (active and inactive)")
    public ResponseEntity<List<AdminPlanResponse>> getAllPlans() {
        List<AdminPlanResponse> plans = catalogService.getAllPlans().stream()
                .map(this::toPlanResponse)
                .toList();
        return ResponseEntity.ok(plans);
    }

    @PostMapping("/plans")
    @Operation(summary = "Create a new membership plan")
    public ResponseEntity<AdminPlanResponse> createPlan(
            @Valid @RequestBody CreatePlanRequest request) {
        var entity = catalogService.createPlan(
                request.name(), request.durationDays(), request.basePriceCents(),
                request.currency(), request.active());
        return ResponseEntity.status(HttpStatus.CREATED).body(toPlanResponse(entity));
    }

    @PatchMapping("/plans/{planId}/status")
    @Operation(summary = "Activate or deactivate a plan")
    public ResponseEntity<AdminPlanResponse> togglePlan(
            @PathVariable UUID planId,
            @RequestParam boolean active) {
        var entity = catalogService.togglePlan(planId, active);
        return ResponseEntity.ok(toPlanResponse(entity));
    }

    // =========================================================================
    // TIERS
    // =========================================================================

    @PostMapping("/plans/{planId}/tiers")
    @Operation(summary = "Add a new tier to an existing plan")
    public ResponseEntity<AdminTierResponse> createTier(
            @PathVariable UUID planId,
            @Valid @RequestBody CreateTierRequest request) {
        var entity = catalogService.createTier(
                planId, request.name(), request.rank(), request.priceCents(), request.active());
        return ResponseEntity.status(HttpStatus.CREATED).body(toTierResponse(entity));
    }

    @PatchMapping("/tiers/{tierId}/status")
    @Operation(summary = "Activate or deactivate a tier")
    public ResponseEntity<AdminTierResponse> toggleTier(
            @PathVariable UUID tierId,
            @RequestParam boolean active) {
        var entity = catalogService.toggleTier(tierId, active);
        return ResponseEntity.ok(toTierResponse(entity));
    }

    // =========================================================================
    // BENEFITS
    // =========================================================================

    @GetMapping("/tiers/{tierId}/benefits")
    @Operation(summary = "List all benefits for a tier")
    public ResponseEntity<List<AdminBenefitResponse>> getBenefits(@PathVariable UUID tierId) {
        List<AdminBenefitResponse> benefits = catalogService.getBenefitsForTier(tierId).stream()
                .map(this::toBenefitResponse)
                .toList();
        return ResponseEntity.ok(benefits);
    }

    @PostMapping("/tiers/{tierId}/benefits")
    @Operation(summary = "Add a new benefit to a tier",
               description = """
               **Benefit Types and configJson examples:**
               
               `FREE_DELIVERY`:
               `{"maxFreeDeliveriesPerMonth": 5, "minOrderValueCents": 9900}`
               
               `PERCENTAGE_DISCOUNT`:
               `{"discountPercent": 10, "applicableCategories": ["all"], "maxDiscountCents": 20000}`
               
               `EXCLUSIVE_DEAL`:
               `{"dealCodes": ["DEAL10"], "description": "Member exclusive"}`
               
               `EARLY_ACCESS`:
               `{"hoursBeforeSale": 24}`
               
               `PRIORITY_SUPPORT`:
               `{"supportTier": "gold", "maxWaitMinutes": 10}`
               """)
    public ResponseEntity<AdminBenefitResponse> addBenefit(
            @PathVariable UUID tierId,
            @Valid @RequestBody CreateBenefitRequest request) {
        var entity = catalogService.addBenefit(
                tierId, request.benefitType(), request.configJson(), request.active());
        return ResponseEntity.status(HttpStatus.CREATED).body(toBenefitResponse(entity));
    }

    @PatchMapping("/benefits/{benefitId}/status")
    @Operation(summary = "Activate or deactivate a benefit")
    public ResponseEntity<AdminBenefitResponse> toggleBenefit(
            @PathVariable UUID benefitId,
            @RequestParam boolean active) {
        var entity = catalogService.toggleBenefit(benefitId, active);
        return ResponseEntity.ok(toBenefitResponse(entity));
    }

    // =========================================================================
    // ELIGIBILITY RULES
    // =========================================================================

    @GetMapping("/tiers/{tierId}/eligibility-rules")
    @Operation(summary = "List all eligibility rules for a tier")
    public ResponseEntity<List<AdminEligibilityRuleResponse>> getRules(@PathVariable UUID tierId) {
        List<AdminEligibilityRuleResponse> rules =
                catalogService.getRulesForTier(tierId).stream()
                        .map(this::toRuleResponse)
                        .toList();
        return ResponseEntity.ok(rules);
    }

    @PostMapping("/tiers/{tierId}/eligibility-rules")
    @Operation(summary = "Add an eligibility rule to a tier",
               description = """
               **Rule Types and configJson examples:**
               
               `ORDER_COUNT`: `{"minOrders": 10, "periodDays": 30}`
               
               `ORDER_VALUE`: `{"minValueCents": 500000, "periodDays": 30}`
               
               `COHORT`: `{"allowedCohorts": ["early_adopter", "vip"]}`
               
               **operator**: `AND` = all AND rules must pass, `OR` = any OR rule suffices.
               """)
    public ResponseEntity<AdminEligibilityRuleResponse> addRule(
            @PathVariable UUID tierId,
            @Valid @RequestBody CreateEligibilityRuleRequest request) {
        var entity = catalogService.addEligibilityRule(
                tierId, request.ruleType(), request.configJson(), request.operator());
        return ResponseEntity.status(HttpStatus.CREATED).body(toRuleResponse(entity));
    }

    @DeleteMapping("/eligibility-rules/{ruleId}")
    @Operation(summary = "Delete an eligibility rule (makes tier open to all)")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID ruleId) {
        catalogService.deleteEligibilityRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // TIER PROGRESS (for demo — simulates order history)
    // =========================================================================

    @PostMapping("/users/{userId}/tier-progress")
    @Operation(summary = "[DEMO] Set a user's order count and spend to simulate tier eligibility",
               description = "In production, tier progress is updated by the Order Service. " +
                             "Use this endpoint in demos to simulate users reaching eligibility thresholds.")
    public ResponseEntity<TierProgressResponse> setTierProgress(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateTierProgressRequest request) {
        var entity = progressService.upsertProgress(
                userId, request.subscriptionId(),
                request.orderCount(), request.totalOrderValueCents());
        return ResponseEntity.ok(toProgressResponse(userId, entity));
    }

    @GetMapping("/users/{userId}/tier-progress/{subscriptionId}")
    @Operation(summary = "Get a user's current tier progress")
    public ResponseEntity<TierProgressResponse> getTierProgress(
            @PathVariable UUID userId,
            @PathVariable UUID subscriptionId) {
        var entity = progressService.getProgress(userId, subscriptionId);
        return ResponseEntity.ok(toProgressResponse(userId, entity));
    }

    // =========================================================================
    // Mappers
    // =========================================================================

    private AdminPlanResponse toPlanResponse(MembershipPlanEntity e) {
        return new AdminPlanResponse(e.getId(), e.getName(), e.getDurationDays(),
                e.getBasePriceCents(), e.getCurrency(), e.isActive(), e.getCreatedAt());
    }

    private AdminTierResponse toTierResponse(MembershipTierEntity e) {
        return new AdminTierResponse(e.getId(), e.getPlan().getId(), e.getName(),
                e.getRank(), e.getPriceCents(), e.isActive(), e.getCreatedAt());
    }

    private AdminBenefitResponse toBenefitResponse(TierBenefitEntity e) {
        return new AdminBenefitResponse(e.getId(), e.getTier().getId(),
                e.getBenefitType(), e.getConfigJson(), e.isActive());
    }

    private AdminEligibilityRuleResponse toRuleResponse(TierEligibilityRuleEntity e) {
        return new AdminEligibilityRuleResponse(e.getId(), e.getTier().getId(),
                e.getRuleType(), e.getConfigJson(), e.getOperator());
    }

    private TierProgressResponse toProgressResponse(UUID userId, com.firstclub.membership.infrastructure.persistence.entity.UserTierProgressEntity e) {
        return new TierProgressResponse(userId, e.getSubscriptionId(),
                e.getOrderCount(), e.getTotalOrderValueCents(),
                e.getPeriodStart(), e.getPeriodEnd(), e.getEvaluatedAt());
    }
}
