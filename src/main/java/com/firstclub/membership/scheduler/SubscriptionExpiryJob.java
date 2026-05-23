package com.firstclub.membership.scheduler;

import com.firstclub.membership.application.port.outbound.EventPublisher;
import com.firstclub.membership.application.port.outbound.SubscriptionRepository;
import com.firstclub.membership.common.util.Clock;
import com.firstclub.membership.domain.event.SubscriptionExpiredEvent;
import com.firstclub.membership.domain.model.UserSubscription;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Processes subscriptions whose end_date has passed.
 * Moves them to GRACE_PERIOD (if configured) or EXPIRED.
 * Uses ShedLock + FOR UPDATE SKIP LOCKED to prevent duplicate processing.
 */
@Component
public class SubscriptionExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpiryJob.class);
    private static final int BATCH_SIZE = 100;

    private final SubscriptionRepository subscriptionRepo;
    private final EventPublisher eventPublisher;
    private final Clock clock;
    private final int gracePeriodDays;

    public SubscriptionExpiryJob(SubscriptionRepository subscriptionRepo,
                                  EventPublisher eventPublisher,
                                  Clock clock,
                                  @Value("${membership.grace-period-days:3}") int gracePeriodDays) {
        this.subscriptionRepo = subscriptionRepo;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.gracePeriodDays = gracePeriodDays;
    }

    @Scheduled(fixedDelayString = "${membership.scheduler.expiry-fixed-delay-ms:300000}")
    @SchedulerLock(name = "subscriptionExpiryJob", lockAtLeastFor = "PT4M",
            lockAtMostFor = "PT10M")
    @Transactional
    public void processExpiredSubscriptions() {
        List<UserSubscription> expired = subscriptionRepo.findExpiredActive(clock.now(), BATCH_SIZE);

        if (expired.isEmpty()) {
            return;
        }

        log.info("Processing {} expired subscriptions", expired.size());
        int processed = 0;

        for (UserSubscription subscription : expired) {
            try {
                if (gracePeriodDays > 0) {
                    subscription.enterGracePeriod();
                } else {
                    subscription.expire();
                    eventPublisher.publish(new SubscriptionExpiredEvent(
                            subscription.getId(), subscription.getUserId()));
                }
                subscriptionRepo.save(subscription);
                processed++;
            } catch (Exception e) {
                log.error("Failed to process expiry for subscriptionId={}: {}",
                        subscription.getId(), e.getMessage());
            }
        }

        log.info("Expiry job completed: {} of {} processed", processed, expired.size());
    }
}
