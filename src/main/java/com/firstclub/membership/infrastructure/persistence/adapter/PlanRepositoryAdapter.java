package com.firstclub.membership.infrastructure.persistence.adapter;

import com.firstclub.membership.application.port.outbound.PlanRepository;
import com.firstclub.membership.domain.model.MembershipPlan;
import com.firstclub.membership.infrastructure.persistence.mapper.PlanMapper;
import com.firstclub.membership.infrastructure.persistence.repository.MembershipPlanJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PlanRepositoryAdapter implements PlanRepository {

    private final MembershipPlanJpaRepository jpaRepo;
    private final PlanMapper mapper;

    public PlanRepositoryAdapter(MembershipPlanJpaRepository jpaRepo, PlanMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    @Override
    public List<MembershipPlan> findAllActive() {
        return jpaRepo.findAllActive().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<MembershipPlan> findById(UUID id) {
        return jpaRepo.findById(id).map(mapper::toDomain);
    }
}
