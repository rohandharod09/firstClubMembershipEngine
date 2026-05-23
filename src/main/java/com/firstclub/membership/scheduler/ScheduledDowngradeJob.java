package com.firstclub.membership.scheduler;

import com.firstclub.membership.application.port.outbound.EventPublisher;
import com.firstclub.membership.application.port.outbound.SubscriptionRepository;
import com.firstclub.membership.common.util.Clock;
import com.firstclub.membership.domain.event.TierDowngradedEvent;
import com.firstclub.membership.domain.model.UserSubscription;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Applies scheduled downgrades when the billing period ends.
 * Users who downgraded retain higher tier until period end, then this job applies the change.
 */
@Component
public class ScheduledDowngradeJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledDowngradeJob.class);
    private static final int BATCH_SIZE = 50;

    private final SubscriptionRepository subscriptionRepo;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    public ScheduledDowngradeJob(SubscriptionRepository subscriptionRepo,
                                  EventPublisher eventPublisher,
                                  Clock clock) {
        this.subscriptionRepo = subscriptionRepo;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${membership.scheduler.downgrade-fixed-delay-ms:3600000}")
    @SchedulerLock(name = "scheduledDowngradeJob", lockAtLeastFor = "PT30M",
            lockAtMostFor = "PT1H")
    @Transactional
    public void applyScheduledDowngrades() {
        List<UserSubscription> toDowngrade =
                subscriptionRepo.findScheduledDowngrades(clock.now(), BATCH_SIZE);

        if (toDowngrade.isEmpty()) {
            log.debug("ScheduledDowngradeJob: nothing to process");
            return;
        }

        long start = System.currentTimeMillis();
        log.info("ScheduledDowngradeJob started: {} subscriptions to downgrade", toDowngrade.size());
        int processed = 0;

        for (UserSubscription subscription : toDowngrade) {
            try {
                UUID fromTierId = subscription.getTierId();
                UUID toTierId = subscription.getScheduledTierId();

                subscription.applyScheduledDowngrade();
                subscriptionRepo.save(subscription);

                eventPublisher.publish(new TierDowngradedEvent(
                        subscription.getId(), subscription.getUserId(), fromTierId, toTierId));

                log.info("Downgrade applied: subscriptionId={} from={} to={}",
                        subscription.getId(), fromTierId, toTierId);
                processed++;
            } catch (Exception e) {
                log.error("Failed to apply downgrade for subscriptionId={}: {}",
                        subscription.getId(), e.getMessage());
            }
        }

        log.info("ScheduledDowngradeJob completed: {}/{} applied in {} ms",
                processed, toDowngrade.size(), System.currentTimeMillis() - start);
    }
}
