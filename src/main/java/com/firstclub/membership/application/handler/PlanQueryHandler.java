package com.firstclub.membership.application.handler;

import com.firstclub.membership.application.port.outbound.PlanRepository;
import com.firstclub.membership.application.port.outbound.SubscriptionRepository;
import com.firstclub.membership.application.query.GetActiveSubscriptionQuery;
import com.firstclub.membership.common.exception.SubscriptionNotFoundException;
import com.firstclub.membership.domain.model.MembershipPlan;
import com.firstclub.membership.domain.model.UserSubscription;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlanQueryHandler {

    private final PlanRepository planRepo;
    private final SubscriptionRepository subscriptionRepo;

    public PlanQueryHandler(PlanRepository planRepo, SubscriptionRepository subscriptionRepo) {
        this.planRepo = planRepo;
        this.subscriptionRepo = subscriptionRepo;
    }

    @Cacheable(value = "plans", key = "'all-active'")
    @Transactional(readOnly = true)
    public List<MembershipPlan> getAllActivePlans() {
        return planRepo.findAllActive();
    }

    @Cacheable(value = "activeSubscription", key = "#query.userId().toString()")
    @Transactional(readOnly = true)
    public UserSubscription getActiveSubscription(GetActiveSubscriptionQuery query) {
        return subscriptionRepo.findActiveByUserId(query.userId())
                .orElseThrow(() -> new SubscriptionNotFoundException(
                        "No active subscription found for user: " + query.userId()));
    }
}
