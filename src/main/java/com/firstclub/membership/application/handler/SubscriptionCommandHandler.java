package com.firstclub.membership.application.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstclub.membership.application.command.*;
import com.firstclub.membership.application.port.outbound.*;
import com.firstclub.membership.common.exception.*;
import com.firstclub.membership.common.util.Clock;
import com.firstclub.membership.domain.event.*;
import com.firstclub.membership.domain.model.*;
import com.firstclub.membership.domain.rule.CompositeEligibilityEvaluator;
import com.firstclub.membership.domain.rule.EligibilityContext;
import com.firstclub.membership.domain.service.SubscriptionDomainService;
import com.firstclub.membership.domain.service.TierTransitionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SubscriptionCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionCommandHandler.class);

    private final SubscriptionRepository subscriptionRepo;
    private final PlanRepository planRepo;
    private final TierRepository tierRepo;
    private final PaymentGateway paymentGateway;
    private final EventPublisher eventPublisher;
    private final IdempotencyStore idempotencyStore;
    private final CompositeEligibilityEvaluator eligibilityEvaluator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SubscriptionCommandHandler(
            SubscriptionRepository subscriptionRepo,
            PlanRepository planRepo,
            TierRepository tierRepo,
            PaymentGateway paymentGateway,
            EventPublisher eventPublisher,
            IdempotencyStore idempotencyStore,
            CompositeEligibilityEvaluator eligibilityEvaluator,
            ObjectMapper objectMapper,
            Clock clock) {
        this.subscriptionRepo = subscriptionRepo;
        this.planRepo = planRepo;
        this.tierRepo = tierRepo;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
        this.idempotencyStore = idempotencyStore;
        this.eligibilityEvaluator = eligibilityEvaluator;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    @CacheEvict(value = "activeSubscription", key = "#command.userId().toString()")
    public UserSubscription subscribe(SubscribeCommand command) {
        // 1. Idempotency check
        var existing = idempotencyStore.findByKey(command.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotent subscribe request for key={}", command.idempotencyKey());
            var record = existing.get();
            return subscriptionRepo.findById(record.getResourceId())
                    .orElseThrow(() -> new SubscriptionNotFoundException("Subscription not found"));
        }

        // 2. Conflict check — only one active subscription per user
        subscriptionRepo.findActiveByUserId(command.userId()).ifPresent(sub -> {
            throw new SubscriptionConflictException(
                    "User already has an active subscription: " + sub.getId());
        });

        // 3. Load plan and tier
        MembershipPlan plan = planRepo.findById(command.planId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + command.planId()));
        MembershipTier tier = tierRepo.findActiveById(command.tierId())
                .orElseThrow(() -> new ResourceNotFoundException("Tier not found: " + command.tierId()));

        // 4. Eligibility check
        EligibilityContext context = new EligibilityContext(
                command.userId(), command.orderCount(), command.totalOrderValueCents(),
                command.userCohort(), null);
        eligibilityEvaluator.evaluate(context, tier.getEligibilityRules());

        // 5. Charge payment (outside transaction commit — idempotent via key)
        long totalCents = plan.getBasePriceCents() + tier.getPriceCents();
        String paymentIdempotencyKey = "pay-sub-" + command.idempotencyKey();
        PaymentGateway.PaymentResult payment = paymentGateway.charge(
                command.userId(), totalCents, plan.getCurrency(), paymentIdempotencyKey);

        if (!payment.success()) {
            throw new PaymentFailedException("Payment failed: " + payment.failureReason());
        }

        // 6. Create subscription
        UserSubscription subscription = SubscriptionDomainService.createNew(
                command.userId(), plan, tier, clock.now(), command.autoRenew());
        subscription.activate();

        // 7. Save subscription + record payment + save idempotency record (same transaction)
        UserSubscription saved = subscriptionRepo.save(subscription);

        // 8. Save idempotency record
        idempotencyStore.save(command.idempotencyKey(), "SUBSCRIPTION",
                saved.getId(), toJson(saved));

        // 9. Publish domain event (fires AFTER_COMMIT)
        eventPublisher.publish(new SubscriptionCreatedEvent(
                saved.getId(), saved.getUserId(), saved.getPlanId(), saved.getTierId()));

        log.info("Subscription created: id={} userId={} plan={} tier={}",
                saved.getId(), saved.getUserId(), plan.getName(), tier.getName());
        return saved;
    }

    @Transactional
    @CacheEvict(value = "activeSubscription", key = "#command.userId().toString()")
    public UserSubscription upgrade(UpgradeTierCommand command) {
        var existing = idempotencyStore.findByKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return subscriptionRepo.findById(existing.get().getResourceId())
                    .orElseThrow(() -> new SubscriptionNotFoundException("Subscription not found"));
        }

        UserSubscription subscription = subscriptionRepo.findById(command.subscriptionId())
                .orElseThrow(() -> new SubscriptionNotFoundException(
                        "Subscription not found: " + command.subscriptionId()));

        if (!subscription.getUserId().equals(command.userId())) {
            throw new SubscriptionConflictException("Subscription does not belong to this user.");
        }

        MembershipTier currentTier = tierRepo.findById(subscription.getTierId())
                .orElseThrow(() -> new ResourceNotFoundException("Current tier not found"));
        MembershipTier targetTier = tierRepo.findActiveById(command.targetTierId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Target tier not found: " + command.targetTierId()));

        TierTransitionValidator.validateUpgrade(currentTier, targetTier);

        // Eligibility check for target tier
        EligibilityContext context = new EligibilityContext(
                command.userId(), command.orderCount(), command.totalOrderValueCents(),
                command.userCohort(), subscription.getTierId());
        eligibilityEvaluator.evaluate(context, targetTier.getEligibilityRules());

        // Calculate prorated cost and charge
        long upgradeCost = SubscriptionDomainService.calculateUpgradeCost(
                subscription, currentTier, targetTier, clock.now());

        if (upgradeCost > 0) {
            String paymentKey = "pay-upgrade-" + command.idempotencyKey();
            PaymentGateway.PaymentResult payment = paymentGateway.charge(
                    command.userId(), upgradeCost, "INR", paymentKey);
            if (!payment.success()) {
                throw new PaymentFailedException("Upgrade payment failed: " + payment.failureReason());
            }
        }

        UUID fromTierId = subscription.getTierId();
        subscription.startUpgrade(command.targetTierId());
        subscription.completeUpgrade();

        UserSubscription saved = subscriptionRepo.save(subscription);
        idempotencyStore.save(command.idempotencyKey(), "SUBSCRIPTION",
                saved.getId(), toJson(saved));

        eventPublisher.publish(new TierUpgradedEvent(
                saved.getId(), saved.getUserId(), fromTierId, saved.getTierId()));

        log.info("Tier upgraded: subscriptionId={} from={} to={}",
                saved.getId(), fromTierId, saved.getTierId());
        return saved;
    }

    @Transactional
    @CacheEvict(value = "activeSubscription", key = "#command.userId().toString()")
    public UserSubscription downgrade(DowngradeTierCommand command) {
        var existing = idempotencyStore.findByKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return subscriptionRepo.findById(existing.get().getResourceId())
                    .orElseThrow(() -> new SubscriptionNotFoundException("Subscription not found"));
        }

        UserSubscription subscription = subscriptionRepo.findById(command.subscriptionId())
                .orElseThrow(() -> new SubscriptionNotFoundException(
                        "Subscription not found: " + command.subscriptionId()));

        if (!subscription.getUserId().equals(command.userId())) {
            throw new SubscriptionConflictException("Subscription does not belong to this user.");
        }

        MembershipTier currentTier = tierRepo.findById(subscription.getTierId())
                .orElseThrow(() -> new ResourceNotFoundException("Current tier not found"));
        MembershipTier targetTier = tierRepo.findActiveById(command.targetTierId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Target tier not found: " + command.targetTierId()));

        TierTransitionValidator.validateDowngrade(currentTier, targetTier);

        subscription.scheduleDowngrade(command.targetTierId());

        UserSubscription saved = subscriptionRepo.save(subscription);
        idempotencyStore.save(command.idempotencyKey(), "SUBSCRIPTION",
                saved.getId(), toJson(saved));

        log.info("Downgrade scheduled: subscriptionId={} from={} to={} effectiveAt={}",
                saved.getId(), currentTier.getName(), targetTier.getName(), saved.getEndDate());
        return saved;
    }

    @Transactional
    @CacheEvict(value = "activeSubscription", key = "#command.userId().toString()")
    public UserSubscription cancel(CancelSubscriptionCommand command) {
        var existing = idempotencyStore.findByKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return subscriptionRepo.findById(existing.get().getResourceId())
                    .orElseThrow(() -> new SubscriptionNotFoundException("Subscription not found"));
        }

        UserSubscription subscription = subscriptionRepo.findById(command.subscriptionId())
                .orElseThrow(() -> new SubscriptionNotFoundException(
                        "Subscription not found: " + command.subscriptionId()));

        if (!subscription.getUserId().equals(command.userId())) {
            throw new SubscriptionConflictException("Subscription does not belong to this user.");
        }

        subscription.cancel();

        UserSubscription saved = subscriptionRepo.save(subscription);
        idempotencyStore.save(command.idempotencyKey(), "SUBSCRIPTION",
                saved.getId(), toJson(saved));

        eventPublisher.publish(new SubscriptionCancelledEvent(
                saved.getId(), saved.getUserId(), command.reason()));

        log.info("Subscription cancelled: subscriptionId={} userId={} reason={}",
                saved.getId(), saved.getUserId(), command.reason());
        return saved;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
