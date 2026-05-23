package com.firstclub.membership.api.rest.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstclub.membership.api.rest.dto.response.*;
import com.firstclub.membership.domain.benefit.BenefitResult;
import com.firstclub.membership.domain.benefit.CompositeBenefitEvaluator;
import com.firstclub.membership.domain.model.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SubscriptionResponseMapper {

    private final ObjectMapper objectMapper;

    public SubscriptionResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SubscriptionResponse toResponse(UserSubscription sub) {
        return new SubscriptionResponse(
                sub.getId(), sub.getUserId(), sub.getPlanId(), sub.getTierId(),
                sub.getStatus().name(), sub.getStartDate(), sub.getEndDate(),
                sub.isAutoRenew(), sub.getCancelledAt(), sub.getScheduledTierId(),
                sub.getCreatedAt(), sub.getUpdatedAt());
    }

    public PlanResponse toPlanResponse(MembershipPlan plan) {
        List<TierResponse> tierResponses = plan.getTiers().stream()
                .map(this::toTierResponse)
                .toList();
        return new PlanResponse(plan.getId(), plan.getName(), plan.getDurationDays(),
                plan.getBasePriceCents(), plan.getCurrency(), tierResponses);
    }

    public TierResponse toTierResponse(MembershipTier tier) {
        List<TierResponse.BenefitConfigResponse> benefits = tier.getBenefits().stream()
                .filter(TierBenefit::isActive)
                .map(b -> new TierResponse.BenefitConfigResponse(
                        b.getBenefitType().name(), parseConfig(b.getConfigJson())))
                .toList();
        return new TierResponse(tier.getId(), tier.getName(), tier.getRank(),
                tier.getPriceCents(), benefits);
    }

    public BenefitValidationResponse toBenefitValidationResponse(
            boolean eligible,
            CompositeBenefitEvaluator.AggregatedBenefitResult result,
            UserSubscription subscription,
            MembershipTier tier) {

        if (!eligible || result == null) {
            return new BenefitValidationResponse(false, false, 0,
                    List.of(), null, null);
        }

        List<BenefitResponse> benefitResponses = result.individualResults().stream()
                .map(r -> new BenefitResponse(
                        r.getBenefitType().name(), r.isApplied(),
                        r.getDiscountCents(), r.getDescription()))
                .toList();

        return new BenefitValidationResponse(
                true, result.freeDelivery(), result.totalDiscountCents(),
                benefitResponses,
                subscription.getEndDate(),
                tier.getName());
    }

    private Object parseConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return configJson;
        }
    }
}
