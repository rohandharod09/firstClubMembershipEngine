package com.firstclub.membership.application.handler;

import com.firstclub.membership.application.port.outbound.SubscriptionRepository;
import com.firstclub.membership.application.port.outbound.TierRepository;
import com.firstclub.membership.application.query.BenefitValidationQuery;
import com.firstclub.membership.domain.benefit.BenefitContext;
import com.firstclub.membership.domain.benefit.CompositeBenefitEvaluator;
import com.firstclub.membership.domain.model.MembershipTier;
import com.firstclub.membership.domain.model.UserSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class BenefitValidationHandler {

    private static final Logger log = LoggerFactory.getLogger(BenefitValidationHandler.class);

    private final SubscriptionRepository subscriptionRepo;
    private final TierRepository tierRepo;
    private final CompositeBenefitEvaluator benefitEvaluator;

    public BenefitValidationHandler(SubscriptionRepository subscriptionRepo,
                                    TierRepository tierRepo,
                                    CompositeBenefitEvaluator benefitEvaluator) {
        this.subscriptionRepo = subscriptionRepo;
        this.tierRepo = tierRepo;
        this.benefitEvaluator = benefitEvaluator;
    }

    public record BenefitValidationResult(
            boolean eligible,
            CompositeBenefitEvaluator.AggregatedBenefitResult benefits,
            UserSubscription subscription,
            MembershipTier tier
    ) {}

    @Transactional(readOnly = true)
    public BenefitValidationResult validate(BenefitValidationQuery query) {
        Optional<UserSubscription> subOpt = subscriptionRepo.findActiveByUserId(query.userId());

        if (subOpt.isEmpty()) {
            log.debug("No active subscription for userId={}", query.userId());
            return new BenefitValidationResult(false, null, null, null);
        }

        UserSubscription subscription = subOpt.get();

        // Always check actual expiry time, not just cached status
        if (subscription.isExpiredAt(Instant.now())) {
            log.info("Subscription expired at checkout: subscriptionId={}", subscription.getId());
            return new BenefitValidationResult(false, null, subscription, null);
        }

        MembershipTier tier = tierRepo.findById(subscription.getTierId())
                .orElse(null);

        if (tier == null) {
            return new BenefitValidationResult(false, null, subscription, null);
        }

        BenefitContext context = new BenefitContext(
                query.userId(), query.orderId(), query.orderValueCents(),
                query.orderCategories(), query.deliveryRequested(), tier.getName());

        CompositeBenefitEvaluator.AggregatedBenefitResult result =
                benefitEvaluator.evaluate(context, tier.getBenefits());

        log.info("Benefit validation: userId={} tier={} discountCents={} freeDelivery={}",
                query.userId(), tier.getName(),
                result.totalDiscountCents(), result.freeDelivery());

        return new BenefitValidationResult(true, result, subscription, tier);
    }
}
