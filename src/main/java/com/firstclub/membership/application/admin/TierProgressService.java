package com.firstclub.membership.application.admin;

import com.firstclub.membership.application.port.outbound.SubscriptionRepository;
import com.firstclub.membership.common.exception.ResourceNotFoundException;
import com.firstclub.membership.domain.model.UserSubscription;
import com.firstclub.membership.infrastructure.persistence.entity.UserTierProgressEntity;
import com.firstclub.membership.infrastructure.persistence.repository.UserTierProgressJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Manages user tier progress (order count + spend) used by the eligibility rule engine.
 *
 * In production: the Order Service publishes OrderCompletedEvent → this service's listener
 * increments the counters. In demo/test: use the admin endpoint to set values directly.
 */
@Service
public class TierProgressService {

    private static final Logger log = LoggerFactory.getLogger(TierProgressService.class);

    private final UserTierProgressJpaRepository progressRepo;
    private final SubscriptionRepository subscriptionRepo;

    public TierProgressService(UserTierProgressJpaRepository progressRepo,
                                SubscriptionRepository subscriptionRepo) {
        this.progressRepo = progressRepo;
        this.subscriptionRepo = subscriptionRepo;
    }

    @Transactional
    public UserTierProgressEntity upsertProgress(UUID userId, UUID subscriptionId,
                                                   int orderCount, long totalOrderValueCents) {
        UserSubscription subscription = subscriptionRepo.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription not found: " + subscriptionId));

        if (!subscription.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Subscription does not belong to user: " + userId);
        }

        var existing = progressRepo.findByUserIdAndSubscriptionId(userId, subscriptionId);

        UserTierProgressEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setOrderCount(orderCount);
            entity.setTotalOrderValueCents(totalOrderValueCents);
            entity.setEvaluatedAt(Instant.now());
        } else {
            entity = new UserTierProgressEntity();
            entity.setId(UUID.randomUUID());
            entity.setUserId(userId);
            entity.setSubscriptionId(subscriptionId);
            entity.setOrderCount(orderCount);
            entity.setTotalOrderValueCents(totalOrderValueCents);
            entity.setPeriodStart(Instant.now().minus(30, ChronoUnit.DAYS));
            entity.setPeriodEnd(Instant.now());
            entity.setEvaluatedAt(Instant.now());
            entity.setCreatedAt(Instant.now());
        }

        UserTierProgressEntity saved = progressRepo.save(entity);
        log.info("TierProgress updated: userId={} subscriptionId={} orders={} spend={}",
                userId, subscriptionId, orderCount, totalOrderValueCents);
        return saved;
    }

    @Transactional(readOnly = true)
    public UserTierProgressEntity getProgress(UUID userId, UUID subscriptionId) {
        return progressRepo.findByUserIdAndSubscriptionId(userId, subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No tier progress found for user: " + userId));
    }
}
