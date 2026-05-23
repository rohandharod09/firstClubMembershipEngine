package com.firstclub.membership.infrastructure.persistence.mapper;

import com.firstclub.membership.domain.model.SubscriptionStatus;
import com.firstclub.membership.domain.model.UserSubscription;
import com.firstclub.membership.infrastructure.persistence.entity.UserSubscriptionEntity;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionMapper {

    public UserSubscription toDomain(UserSubscriptionEntity entity) {
        UserSubscription sub = UserSubscription.reconstitute();
        sub.setId(entity.getId());
        sub.setUserId(entity.getUserId());
        sub.setPlanId(entity.getPlanId());
        sub.setTierId(entity.getTierId());
        sub.setStatus(SubscriptionStatus.valueOf(entity.getStatus()));
        sub.setStartDate(entity.getStartDate());
        sub.setEndDate(entity.getEndDate());
        sub.setAutoRenew(entity.isAutoRenew());
        sub.setCancelledAt(entity.getCancelledAt());
        sub.setPreviousTierId(entity.getPreviousTierId());
        sub.setScheduledTierId(entity.getScheduledTierId());
        sub.setVersion(entity.getVersion());
        sub.setCreatedAt(entity.getCreatedAt());
        sub.setUpdatedAt(entity.getUpdatedAt());
        return sub;
    }

    public UserSubscriptionEntity toEntity(UserSubscription domain) {
        UserSubscriptionEntity entity = new UserSubscriptionEntity();
        entity.setId(domain.getId());
        entity.setUserId(domain.getUserId());
        entity.setPlanId(domain.getPlanId());
        entity.setTierId(domain.getTierId());
        entity.setStatus(domain.getStatus().name());
        entity.setStartDate(domain.getStartDate());
        entity.setEndDate(domain.getEndDate());
        entity.setAutoRenew(domain.isAutoRenew());
        entity.setCancelledAt(domain.getCancelledAt());
        entity.setPreviousTierId(domain.getPreviousTierId());
        entity.setScheduledTierId(domain.getScheduledTierId());
        entity.setVersion(domain.getVersion());
        entity.setCreatedAt(domain.getCreatedAt() != null
                ? domain.getCreatedAt() : java.time.Instant.now());
        entity.setUpdatedAt(domain.getUpdatedAt() != null
                ? domain.getUpdatedAt() : java.time.Instant.now());
        return entity;
    }
}
