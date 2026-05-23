package com.firstclub.membership.infrastructure.persistence.adapter;

import com.firstclub.membership.application.port.outbound.SubscriptionRepository;
import com.firstclub.membership.domain.model.UserSubscription;
import com.firstclub.membership.infrastructure.persistence.mapper.SubscriptionMapper;
import com.firstclub.membership.infrastructure.persistence.repository.UserSubscriptionJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SubscriptionRepositoryAdapter implements SubscriptionRepository {

    private final UserSubscriptionJpaRepository jpaRepo;
    private final SubscriptionMapper mapper;

    public SubscriptionRepositoryAdapter(UserSubscriptionJpaRepository jpaRepo,
                                         SubscriptionMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    @Override
    public UserSubscription save(UserSubscription subscription) {
        var entity = mapper.toEntity(subscription);
        var saved = jpaRepo.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<UserSubscription> findById(UUID id) {
        return jpaRepo.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<UserSubscription> findActiveByUserId(UUID userId) {
        return jpaRepo.findActiveByUserId(userId).map(mapper::toDomain);
    }

    @Override
    public List<UserSubscription> findExpiredActive(Instant now, int limit) {
        return jpaRepo.findExpiredActiveSubscriptions(now, limit).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public List<UserSubscription> findGracePeriodAutoRenew(int limit) {
        return jpaRepo.findGracePeriodAutoRenewSubscriptions(limit).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public List<UserSubscription> findScheduledDowngrades(Instant now, int limit) {
        return jpaRepo.findScheduledDowngrades(now, limit).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public List<UserSubscription> findAllActive() {
        return jpaRepo.findAllActiveSubscriptions().stream()
                .map(mapper::toDomain).toList();
    }
}
