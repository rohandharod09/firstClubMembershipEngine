package com.firstclub.membership.application.port.outbound;

import com.firstclub.membership.domain.model.MembershipPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository {

    List<MembershipPlan> findAllActive();

    Optional<MembershipPlan> findById(UUID id);
}
