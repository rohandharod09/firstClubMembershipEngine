package com.firstclub.membership.infrastructure.event;

import com.firstclub.membership.domain.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to domain events AFTER the transaction commits.
 * In production, replace with Kafka publisher or notification service calls.
 */
@Component
public class SubscriptionEventListener {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEventListener.class);

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubscriptionCreated(SubscriptionCreatedEvent event) {
        log.info("EVENT [{}] subscriptionId={} userId={} planId={} tierId={}",
                event.getEventType(), event.getSubscriptionId(),
                event.getUserId(), event.getPlanId(), event.getTierId());
        // TODO: send welcome email, update analytics, trigger CRM workflow
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubscriptionCancelled(SubscriptionCancelledEvent event) {
        log.info("EVENT [{}] subscriptionId={} userId={} reason={}",
                event.getEventType(), event.getSubscriptionId(),
                event.getUserId(), event.getReason());
        // TODO: send retention offer, update analytics
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubscriptionExpired(SubscriptionExpiredEvent event) {
        log.info("EVENT [{}] subscriptionId={} userId={}",
                event.getEventType(), event.getSubscriptionId(), event.getUserId());
        // TODO: send win-back email, update analytics
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTierUpgraded(TierUpgradedEvent event) {
        log.info("EVENT [{}] subscriptionId={} userId={} from={} to={}",
                event.getEventType(), event.getSubscriptionId(), event.getUserId(),
                event.getFromTierId(), event.getToTierId());
        // TODO: send congratulations email
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTierDowngraded(TierDowngradedEvent event) {
        log.info("EVENT [{}] subscriptionId={} userId={} from={} to={}",
                event.getEventType(), event.getSubscriptionId(), event.getUserId(),
                event.getFromTierId(), event.getToTierId());
        // TODO: send downgrade confirmation, collect feedback
    }
}
