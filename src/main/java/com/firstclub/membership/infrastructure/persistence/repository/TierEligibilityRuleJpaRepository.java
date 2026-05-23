package com.firstclub.membership.infrastructure.persistence.repository;

import com.firstclub.membership.infrastructure.persistence.entity.TierEligibilityRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TierEligibilityRuleJpaRepository extends JpaRepository<TierEligibilityRuleEntity, UUID> {

    List<TierEligibilityRuleEntity> findByTierId(UUID tierId);
}
