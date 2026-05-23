package com.firstclub.membership.infrastructure.persistence.repository;

import com.firstclub.membership.infrastructure.persistence.entity.MembershipPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MembershipPlanJpaRepository extends JpaRepository<MembershipPlanEntity, UUID> {

    @Query("SELECT p FROM MembershipPlanEntity p WHERE p.active = true ORDER BY p.name")
    List<MembershipPlanEntity> findAllActive();
}
