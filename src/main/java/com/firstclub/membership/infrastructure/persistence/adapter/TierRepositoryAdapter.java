package com.firstclub.membership.infrastructure.persistence.adapter;

import com.firstclub.membership.application.port.outbound.TierRepository;
import com.firstclub.membership.domain.model.MembershipTier;
import com.firstclub.membership.infrastructure.persistence.mapper.PlanMapper;
import com.firstclub.membership.infrastructure.persistence.repository.MembershipTierJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class TierRepositoryAdapter implements TierRepository {

    private final MembershipTierJpaRepository jpaRepo;
    private final PlanMapper mapper;

    public TierRepositoryAdapter(MembershipTierJpaRepository jpaRepo, PlanMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    @Override
    public Optional<MembershipTier> findById(UUID id) {
        return jpaRepo.findById(id).map(mapper::tierToDomain);
    }

    @Override
    public Optional<MembershipTier> findActiveById(UUID id) {
        return jpaRepo.findByIdAndActiveTrue(id).map(mapper::tierToDomain);
    }
}
