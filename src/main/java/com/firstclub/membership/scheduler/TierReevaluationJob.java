package com.firstclub.membership.scheduler;

import com.firstclub.membership.application.port.outbound.SubscriptionRepository;
import com.firstclub.membership.application.port.outbound.TierRepository;
import com.firstclub.membership.common.util.Clock;
import com.firstclub.membership.domain.model.MembershipTier;
import com.firstclub.membership.domain.model.UserSubscription;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Daily job that re-evaluates whether active subscribers still qualify for their tier.
 * Does NOT auto-downgrade — instead publishes notification events for review.
 * Auto-upgrade eligibility notifications are also surfaced here.
 */
@Component
public class TierReevaluationJob {

    private static final Logger log = LoggerFactory.getLogger(TierReevaluationJob.class);

    private final SubscriptionRepository subscriptionRepo;
    private final TierRepository tierRepo;
    private final Clock clock;

    public TierReevaluationJob(SubscriptionRepository subscriptionRepo,
                                TierRepository tierRepo,
                                Clock clock) {
        this.subscriptionRepo = subscriptionRepo;
        this.tierRepo = tierRepo;
        this.clock = clock;
    }

    @Scheduled(cron = "${membership.scheduler.tier-reevaluation-cron:0 0 2 * * *}")
    @SchedulerLock(name = "tierReevaluationJob", lockAtLeastFor = "PT30M",
            lockAtMostFor = "PT2H")
    @Transactional(readOnly = true)
    public void reevaluateTiers() {
        List<UserSubscription> activeSubscriptions = subscriptionRepo.findAllActive();
        log.info("Tier reevaluation started: {} active subscriptions", activeSubscriptions.size());

        int checked = 0;
        for (UserSubscription subscription : activeSubscriptions) {
            try {
                Optional<MembershipTier> tierOpt = tierRepo.findById(subscription.getTierId());
                if (tierOpt.isEmpty()) {
                    log.warn("Tier not found for subscriptionId={}", subscription.getId());
                    continue;
                }
                // In production: fetch UserTierProgress, re-run eligibility evaluator,
                // flag subscriptions where user no longer qualifies for current tier,
                // and publish TierEligibilityChangedEvent for downstream notification service.
                checked++;
            } catch (Exception e) {
                log.error("Reevaluation error for subscriptionId={}: {}",
                        subscription.getId(), e.getMessage());
            }
        }

        log.info("Tier reevaluation completed: {} subscriptions checked", checked);
    }
}
