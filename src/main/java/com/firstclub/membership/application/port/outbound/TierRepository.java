package com.firstclub.membership.application.port.outbound;

import com.firstclub.membership.domain.model.MembershipTier;

import java.util.Optional;
import java.util.UUID;

public interface TierRepository {

    Optional<MembershipTier> findById(UUID id);

    Optional<MembershipTier> findActiveById(UUID id);
}
