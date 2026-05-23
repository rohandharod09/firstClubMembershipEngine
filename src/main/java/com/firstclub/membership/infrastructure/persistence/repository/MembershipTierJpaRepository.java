package com.firstclub.membership.infrastructure.persistence.repository;

import com.firstclub.membership.infrastructure.persistence.entity.MembershipTierEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipTierJpaRepository extends JpaRepository<MembershipTierEntity, UUID> {

    List<MembershipTierEntity> findByPlanIdAndActiveTrue(UUID planId);

    Optional<MembershipTierEntity> findByIdAndActiveTrue(UUID id);
}
