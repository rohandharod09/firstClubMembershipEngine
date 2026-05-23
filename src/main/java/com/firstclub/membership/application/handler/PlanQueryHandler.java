package com.firstclub.membership.application.handler;

import com.firstclub.membership.application.port.outbound.PlanRepository;
import com.firstclub.membership.application.port.outbound.SubscriptionRepository;
import com.firstclub.membership.application.query.GetActiveSubscriptionQuery;
import com.firstclub.membership.common.exception.SubscriptionNotFoundException;
import com.firstclub.membership.domain.model.MembershipPlan;
import com.firstclub.membership.domain.model.UserSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlanQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(PlanQueryHandler.class);

    private final PlanRepository planRepo;
    private final SubscriptionRepository subscriptionRepo;

    public PlanQueryHandler(PlanRepository planRepo, SubscriptionRepository subscriptionRepo) {
        this.planRepo = planRepo;
        this.subscriptionRepo = subscriptionRepo;
    }

    // Spring logs a cache miss by calling this method; a cache hit skips the method entirely.
    // The log below will appear only on cache misses (DB reads). Use redis-cli to verify hits:
    //   redis-cli KEYS "*plans*"
    @Cacheable(value = "plans", key = "'all-active'")
    @Transactional(readOnly = true)
    public List<MembershipPlan> getAllActivePlans() {
        log.debug("cache MISS: loading all active plans from DB");
        List<MembershipPlan> plans = planRepo.findAllActive();
        log.info("getAllActivePlans: loaded {} plans from DB (cached for 5 min)", plans.size());
        return plans;
    }

    // Similarly, this log appears only on cache misses for the subscription.
    @Cacheable(value = "activeSubscription", key = "#query.userId().toString()")
    @Transactional(readOnly = true)
    public UserSubscription getActiveSubscription(GetActiveSubscriptionQuery query) {
        log.debug("cache MISS: loading active subscription from DB for userId={}", query.userId());
        return subscriptionRepo.findActiveByUserId(query.userId())
                .orElseThrow(() -> new SubscriptionNotFoundException(
                        "No active subscription found for user: " + query.userId()));
    }
}
