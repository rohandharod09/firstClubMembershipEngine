package com.firstclub.membership.infrastructure.persistence.repository;

import com.firstclub.membership.infrastructure.persistence.entity.TierBenefitEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TierBenefitJpaRepository extends JpaRepository<TierBenefitEntity, UUID> {

    List<TierBenefitEntity> findByTierIdAndActiveTrue(UUID tierId);
}
