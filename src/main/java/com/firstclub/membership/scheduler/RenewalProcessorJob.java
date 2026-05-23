package com.firstclub.membership.scheduler;

import com.firstclub.membership.application.port.outbound.EventPublisher;
import com.firstclub.membership.application.port.outbound.PaymentGateway;
import com.firstclub.membership.application.port.outbound.PlanRepository;
import com.firstclub.membership.application.port.outbound.SubscriptionRepository;
import com.firstclub.membership.domain.event.SubscriptionExpiredEvent;
import com.firstclub.membership.domain.model.MembershipPlan;
import com.firstclub.membership.domain.model.UserSubscription;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Processes GRACE_PERIOD subscriptions with auto_renew=true.
 * Attempts payment renewal. On success, extends subscription. On failure, expires.
 */
@Component
public class RenewalProcessorJob {

    private static final Logger log = LoggerFactory.getLogger(RenewalProcessorJob.class);
    private static final int BATCH_SIZE = 50;

    private final SubscriptionRepository subscriptionRepo;
    private final PlanRepository planRepo;
    private final PaymentGateway paymentGateway;
    private final EventPublisher eventPublisher;

    public RenewalProcessorJob(SubscriptionRepository subscriptionRepo,
                                PlanRepository planRepo,
                                PaymentGateway paymentGateway,
                                EventPublisher eventPublisher) {
        this.subscriptionRepo = subscriptionRepo;
        this.planRepo = planRepo;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${membership.scheduler.renewal-fixed-delay-ms:900000}")
    @SchedulerLock(name = "renewalProcessorJob", lockAtLeastFor = "PT10M",
            lockAtMostFor = "PT20M")
    @Transactional
    public void processRenewals() {
        List<UserSubscription> gracePeriodSubs =
                subscriptionRepo.findGracePeriodAutoRenew(BATCH_SIZE);

        if (gracePeriodSubs.isEmpty()) {
            return;
        }

        log.info("Processing {} renewal candidates", gracePeriodSubs.size());

        for (UserSubscription subscription : gracePeriodSubs) {
            try {
                processRenewal(subscription);
            } catch (Exception e) {
                log.error("Renewal failed for subscriptionId={}: {}",
                        subscription.getId(), e.getMessage());
            }
        }
    }

    private void processRenewal(UserSubscription subscription) {
        Optional<MembershipPlan> planOpt = planRepo.findById(subscription.getPlanId());
        if (planOpt.isEmpty()) {
            log.warn("Plan not found for subscriptionId={}", subscription.getId());
            expireSubscription(subscription);
            return;
        }

        MembershipPlan plan = planOpt.get();
        long renewalAmount = plan.getBasePriceCents();
        String idempotencyKey = "renew-" + subscription.getId() + "-" + UUID.randomUUID();

        PaymentGateway.PaymentResult result = paymentGateway.charge(
                subscription.getUserId(), renewalAmount, plan.getCurrency(), idempotencyKey);

        if (result.success()) {
            subscription.renew(plan.getDurationDays());
            subscriptionRepo.save(subscription);
            log.info("Renewal successful: subscriptionId={}", subscription.getId());
        } else {
            log.warn("Renewal payment failed: subscriptionId={} reason={}",
                    subscription.getId(), result.failureReason());
            expireSubscription(subscription);
        }
    }

    private void expireSubscription(UserSubscription subscription) {
        subscription.expire();
        subscriptionRepo.save(subscription);
        eventPublisher.publish(new SubscriptionExpiredEvent(
                subscription.getId(), subscription.getUserId()));
    }
}
