package com.firstclub.membership.application.admin;

import com.firstclub.membership.common.exception.ResourceNotFoundException;
import com.firstclub.membership.infrastructure.persistence.entity.*;
import com.firstclub.membership.infrastructure.persistence.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin service for catalog management: creating plans, tiers, benefits, and eligibility rules.
 * Every write evicts the plans/benefits/rules caches so consumers always see fresh data.
 */
@Service
public class AdminCatalogService {

    private static final Logger log = LoggerFactory.getLogger(AdminCatalogService.class);

    private final MembershipPlanJpaRepository planRepo;
    private final MembershipTierJpaRepository tierRepo;
    private final TierBenefitJpaRepository benefitRepo;
    private final TierEligibilityRuleJpaRepository ruleRepo;

    public AdminCatalogService(MembershipPlanJpaRepository planRepo,
                                MembershipTierJpaRepository tierRepo,
                                TierBenefitJpaRepository benefitRepo,
                                TierEligibilityRuleJpaRepository ruleRepo) {
        this.planRepo = planRepo;
        this.tierRepo = tierRepo;
        this.benefitRepo = benefitRepo;
        this.ruleRepo = ruleRepo;
    }

    // -------------------------------------------------------------------------
    // PLANS
    // -------------------------------------------------------------------------

    @Transactional
    @CacheEvict(value = "plans", allEntries = true)
    public MembershipPlanEntity createPlan(String name, int durationDays, long basePriceCents,
                                            String currency, boolean active) {
        MembershipPlanEntity entity = new MembershipPlanEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(name);
        entity.setDurationDays(durationDays);
        entity.setBasePriceCents(basePriceCents);
        entity.setCurrency(currency);
        entity.setActive(active);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        MembershipPlanEntity saved = planRepo.save(entity);
        log.info("Admin: created plan id={} name={}", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    @CacheEvict(value = "plans", allEntries = true)
    public MembershipPlanEntity togglePlan(UUID planId, boolean active) {
        MembershipPlanEntity plan = planRepo.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planId));
        plan.setActive(active);
        plan.setUpdatedAt(Instant.now());
        return planRepo.save(plan);
    }

    @Transactional(readOnly = true)
    public List<MembershipPlanEntity> getAllPlans() {
        return planRepo.findAll();
    }

    // -------------------------------------------------------------------------
    // TIERS
    // -------------------------------------------------------------------------

    @Transactional
    @CacheEvict(value = "plans", allEntries = true)
    public MembershipTierEntity createTier(UUID planId, String name, int rank,
                                            long priceCents, boolean active) {
        MembershipPlanEntity plan = planRepo.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planId));

        MembershipTierEntity entity = new MembershipTierEntity();
        entity.setId(UUID.randomUUID());
        entity.setPlan(plan);
        entity.setName(name);
        entity.setRank(rank);
        entity.setPriceCents(priceCents);
        entity.setActive(active);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        MembershipTierEntity saved = tierRepo.save(entity);
        log.info("Admin: created tier id={} name={} for planId={}", saved.getId(), name, planId);
        return saved;
    }

    @Transactional
    @CacheEvict(value = "plans", allEntries = true)
    public MembershipTierEntity toggleTier(UUID tierId, boolean active) {
        MembershipTierEntity tier = tierRepo.findById(tierId)
                .orElseThrow(() -> new ResourceNotFoundException("Tier not found: " + tierId));
        tier.setActive(active);
        tier.setUpdatedAt(Instant.now());
        return tierRepo.save(tier);
    }

    // -------------------------------------------------------------------------
    // BENEFITS
    // -------------------------------------------------------------------------

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "plans", allEntries = true),
            @CacheEvict(value = "tierBenefits", allEntries = true)
    })
    public TierBenefitEntity addBenefit(UUID tierId, String benefitType, String configJson,
                                         boolean active) {
        MembershipTierEntity tier = tierRepo.findById(tierId)
                .orElseThrow(() -> new ResourceNotFoundException("Tier not found: " + tierId));

        TierBenefitEntity entity = new TierBenefitEntity();
        entity.setId(UUID.randomUUID());
        entity.setTier(tier);
        entity.setBenefitType(benefitType.toUpperCase());
        entity.setConfigJson(configJson);
        entity.setActive(active);
        entity.setCreatedAt(Instant.now());
        TierBenefitEntity saved = benefitRepo.save(entity);
        log.info("Admin: added benefit id={} type={} to tierId={}", saved.getId(), benefitType, tierId);
        return saved;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "plans", allEntries = true),
            @CacheEvict(value = "tierBenefits", allEntries = true)
    })
    public TierBenefitEntity toggleBenefit(UUID benefitId, boolean active) {
        TierBenefitEntity benefit = benefitRepo.findById(benefitId)
                .orElseThrow(() -> new ResourceNotFoundException("Benefit not found: " + benefitId));
        benefit.setActive(active);
        return benefitRepo.save(benefit);
    }

    @Transactional(readOnly = true)
    public List<TierBenefitEntity> getBenefitsForTier(UUID tierId) {
        return benefitRepo.findByTierIdAndActiveTrue(tierId);
    }

    // -------------------------------------------------------------------------
    // ELIGIBILITY RULES
    // -------------------------------------------------------------------------

    @Transactional
    @CacheEvict(value = "eligibilityRules", allEntries = true)
    public TierEligibilityRuleEntity addEligibilityRule(UUID tierId, String ruleType,
                                                         String configJson, String operator) {
        MembershipTierEntity tier = tierRepo.findById(tierId)
                .orElseThrow(() -> new ResourceNotFoundException("Tier not found: " + tierId));

        TierEligibilityRuleEntity entity = new TierEligibilityRuleEntity();
        entity.setId(UUID.randomUUID());
        entity.setTier(tier);
        entity.setRuleType(ruleType.toUpperCase());
        entity.setConfigJson(configJson);
        entity.setOperator(operator.toUpperCase());
        entity.setCreatedAt(Instant.now());
        TierEligibilityRuleEntity saved = ruleRepo.save(entity);
        log.info("Admin: added eligibility rule id={} type={} to tierId={}", saved.getId(), ruleType, tierId);
        return saved;
    }

    @Transactional
    @CacheEvict(value = "eligibilityRules", allEntries = true)
    public void deleteEligibilityRule(UUID ruleId) {
        if (!ruleRepo.existsById(ruleId)) {
            throw new ResourceNotFoundException("Eligibility rule not found: " + ruleId);
        }
        ruleRepo.deleteById(ruleId);
        log.info("Admin: deleted eligibility rule id={}", ruleId);
    }

    @Transactional(readOnly = true)
    public List<TierEligibilityRuleEntity> getRulesForTier(UUID tierId) {
        return ruleRepo.findByTierId(tierId);
    }
}
