package com.firstclub.membership.infrastructure.persistence.mapper;

import com.firstclub.membership.domain.model.*;
import com.firstclub.membership.infrastructure.persistence.entity.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PlanMapper {

    public MembershipPlan toDomain(MembershipPlanEntity entity) {
        List<MembershipTier> tiers = entity.getTiers().stream()
                .filter(MembershipTierEntity::isActive)
                .map(this::tierToDomain)
                .toList();
        return new MembershipPlan(
                entity.getId(), entity.getName(), entity.getDurationDays(),
                entity.getBasePriceCents(), entity.getCurrency(), entity.isActive(), tiers);
    }

    public MembershipTier tierToDomain(MembershipTierEntity entity) {
        List<TierBenefit> benefits = entity.getBenefits().stream()
                .filter(TierBenefitEntity::isActive)
                .map(this::benefitToDomain)
                .toList();
        List<TierEligibilityRule> rules = entity.getEligibilityRules().stream()
                .map(this::ruleToDomain)
                .toList();
        return new MembershipTier(
                entity.getId(), entity.getPlan().getId(), entity.getName(),
                entity.getRank(), entity.getPriceCents(), entity.isActive(), benefits, rules);
    }

    public TierBenefit benefitToDomain(TierBenefitEntity entity) {
        BenefitType benefitType = BenefitType.valueOf(entity.getBenefitType());
        return new TierBenefit(
                entity.getId(), entity.getTier().getId(),
                benefitType, entity.getConfigJson(), entity.isActive());
    }

    public TierEligibilityRule ruleToDomain(TierEligibilityRuleEntity entity) {
        return new TierEligibilityRule(
                entity.getId(), entity.getTier().getId(),
                entity.getRuleType(), entity.getConfigJson(), entity.getOperator());
    }
}
