package com.firstclub.membership.infrastructure.persistence.repository;

import com.firstclub.membership.infrastructure.persistence.entity.UserSubscriptionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSubscriptionJpaRepository extends JpaRepository<UserSubscriptionEntity, UUID> {

    @Query("SELECT s FROM UserSubscriptionEntity s WHERE s.userId = :userId " +
           "AND s.status IN ('ACTIVE', 'GRACE_PERIOD', 'UPGRADE_PENDING', 'DOWNGRADE_SCHEDULED')")
    Optional<UserSubscriptionEntity> findActiveByUserId(@Param("userId") UUID userId);

    @Query(value = "SELECT * FROM user_subscriptions " +
                   "WHERE status = 'ACTIVE' AND end_date < :now " +
                   "LIMIT :limit FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<UserSubscriptionEntity> findExpiredActiveSubscriptions(
            @Param("now") Instant now, @Param("limit") int limit);

    @Query(value = "SELECT * FROM user_subscriptions " +
                   "WHERE status = 'GRACE_PERIOD' AND auto_renew = true " +
                   "LIMIT :limit FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<UserSubscriptionEntity> findGracePeriodAutoRenewSubscriptions(@Param("limit") int limit);

    @Query(value = "SELECT * FROM user_subscriptions " +
                   "WHERE status = 'DOWNGRADE_SCHEDULED' AND end_date <= :now " +
                   "LIMIT :limit FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<UserSubscriptionEntity> findScheduledDowngrades(
            @Param("now") Instant now, @Param("limit") int limit);

    @Query("SELECT s FROM UserSubscriptionEntity s WHERE s.status = 'ACTIVE'")
    List<UserSubscriptionEntity> findAllActiveSubscriptions();
}
