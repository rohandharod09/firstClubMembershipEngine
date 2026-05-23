package com.firstclub.membership.infrastructure.persistence.repository;

import com.firstclub.membership.infrastructure.persistence.entity.UserTierProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserTierProgressJpaRepository extends JpaRepository<UserTierProgressEntity, UUID> {

    Optional<UserTierProgressEntity> findByUserIdAndSubscriptionId(UUID userId, UUID subscriptionId);
}
