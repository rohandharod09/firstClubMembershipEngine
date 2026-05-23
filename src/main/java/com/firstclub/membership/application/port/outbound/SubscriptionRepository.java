package com.firstclub.membership.application.port.outbound;

import com.firstclub.membership.domain.model.UserSubscription;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository {

    UserSubscription save(UserSubscription subscription);

    Optional<UserSubscription> findById(UUID id);

    Optional<UserSubscription> findActiveByUserId(UUID userId);

    List<UserSubscription> findExpiredActive(Instant now, int limit);

    List<UserSubscription> findGracePeriodAutoRenew(int limit);

    List<UserSubscription> findScheduledDowngrades(Instant now, int limit);

    List<UserSubscription> findAllActive();
}
