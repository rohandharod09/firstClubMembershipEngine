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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates subscription lifecycle commands.
 *
 * ─── Two separate deduplication mechanisms (different responsibilities) ────────
 *
 * 1. idempotency_records (PostgreSQL)
 *    Prevents duplicate mutations (subscribe, upgrade, cancel).
 *    Written in the SAME DB transaction as the subscription row — so if the
 *    commit fails, both are rolled back atomically. Survives app restarts and
 *    Redis flushes. TTL = 24 hours (configurable).
 *
 * 2. Redis @Cacheable / @CacheEvict
 *    Caches READ responses only — active subscription lookup (30 s TTL) and
 *    plans catalog (5 min TTL). Purely a performance optimization that keeps
 *    the checkout benefit-validation path off the DB under high load.
 *    Volatile: if Redis restarts, the cache warms itself from the DB on the
 *    next request. We never use Redis to enforce correctness.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Service
public class SubscriptionCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionCommandHandler.class);

    private final SubscriptionRepository subscriptionRepo;
    private final PlanRepository planRepo;
    private final TierRepository tierRepo;
    private final PaymentGateway paymentGateway;
    private final PaymentTransactionRepository paymentTxnRepo;   // ← persists to DB
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
            PaymentTransactionRepository paymentTxnRepo,
            EventPublisher eventPublisher,
            IdempotencyStore idempotencyStore,
            CompositeEligibilityEvaluator eligibilityEvaluator,
            ObjectMapper objectMapper,
            Clock clock) {
        this.subscriptionRepo = subscriptionRepo;
        this.planRepo = planRepo;
        this.tierRepo = tierRepo;
        this.paymentGateway = paymentGateway;
        this.paymentTxnRepo = paymentTxnRepo;
        this.eventPublisher = eventPublisher;
        this.idempotencyStore = idempotencyStore;
        this.eligibilityEvaluator = eligibilityEvaluator;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    @CacheEvict(value = "activeSubscription", key = "#command.userId().toString()")
    public UserSubscription subscribe(SubscribeCommand command) {

        // ── Step 1: Idempotency check (PostgreSQL — durable, transactional) ──────
        var existing = idempotencyStore.findByKey(command.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotent subscribe hit: key={}", command.idempotencyKey());
            return subscriptionRepo.findById(existing.get().getResourceId())
                    .orElseThrow(() -> new SubscriptionNotFoundException("Subscription not found"));
        }

        // ── Step 2: Guard — one active subscription per user ─────────────────────
        subscriptionRepo.findActiveByUserId(command.userId()).ifPresent(sub -> {
            throw new SubscriptionConflictException(
                    "User already has an active subscription: " + sub.getId());
        });

        // ── Step 3: Load catalog (plan + tier) ───────────────────────────────────
        MembershipPlan plan = planRepo.findById(command.planId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + command.planId()));
        MembershipTier tier = tierRepo.findActiveById(command.tierId())
                .orElseThrow(() -> new ResourceNotFoundException("Tier not found: " + command.tierId()));

        // ── Step 4: Eligibility check ─────────────────────────────────────────────
        EligibilityContext context = new EligibilityContext(
                command.userId(), command.orderCount(), command.totalOrderValueCents(),
                command.userCohort(), null);
        eligibilityEvaluator.evaluate(context, tier.getEligibilityRules());

        // ── Step 5: Charge payment (BEFORE DB commit, safe to retry via key) ──────
        // The payment gateway receives a stable idempotency key derived from the
        // API-level key. If the network fails after the charge succeeds but before
        // the DB commits, the retry will hit the idempotency_records check and
        // return the existing subscription without double-charging.
        long totalCents = plan.getBasePriceCents() + tier.getPriceCents();
        String paymentKey = "pay-sub-" + command.idempotencyKey();
        PaymentGateway.PaymentResult payment =
                paymentGateway.charge(command.userId(), totalCents, plan.getCurrency(), paymentKey);

        if (!payment.success()) {
            throw new PaymentFailedException("Payment failed: " + payment.failureReason());
        }

        // ── Step 6: Create subscription domain object ─────────────────────────────
        UserSubscription subscription = SubscriptionDomainService.createNew(
                command.userId(), plan, tier, clock.now(), command.autoRenew());
        subscription.activate();

        // ── Step 7: Persist — subscription + payment tx + idempotency record ──────
        //    All three writes happen inside the SAME @Transactional boundary.
        //    If any write fails, ALL are rolled back — no orphaned payment records,
        //    no ghost idempotency entries, no subscription without a payment audit trail.
        UserSubscription saved = subscriptionRepo.save(subscription);

        PaymentTransaction txn = PaymentTransaction.create(
                saved.getId(), command.userId(), totalCents, plan.getCurrency(), paymentKey);
        txn.markSuccess(payment.externalTxnId());
        paymentTxnRepo.save(txn);

        idempotencyStore.save(command.idempotencyKey(), "SUBSCRIPTION",
                saved.getId(), toJson(saved));

        // ── Step 8: Publish domain event (fires AFTER_COMMIT via @TransactionalEventListener)
        eventPublisher.publish(new SubscriptionCreatedEvent(
                saved.getId(), saved.getUserId(), saved.getPlanId(), saved.getTierId()));

        log.info("Subscription created: id={} userId={} plan={} tier={} amountCents={}",
                saved.getId(), saved.getUserId(), plan.getName(), tier.getName(), totalCents);
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

        EligibilityContext context = new EligibilityContext(
                command.userId(), command.orderCount(), command.totalOrderValueCents(),
                command.userCohort(), subscription.getTierId());
        eligibilityEvaluator.evaluate(context, targetTier.getEligibilityRules());

        long upgradeCost = SubscriptionDomainService.calculateUpgradeCost(
                subscription, currentTier, targetTier, clock.now());

        if (upgradeCost > 0) {
            String paymentKey = "pay-upgrade-" + command.idempotencyKey();
            PaymentGateway.PaymentResult payment =
                    paymentGateway.charge(command.userId(), upgradeCost, "INR", paymentKey);
            if (!payment.success()) {
                throw new PaymentFailedException("Upgrade payment failed: " + payment.failureReason());
            }

            PaymentTransaction txn = PaymentTransaction.create(
                    subscription.getId(), command.userId(), upgradeCost, "INR", paymentKey);
            txn.markSuccess(payment.externalTxnId());
            paymentTxnRepo.save(txn);
        }

        UUID fromTierId = subscription.getTierId();
        subscription.startUpgrade(command.targetTierId());
        subscription.completeUpgrade();

        UserSubscription saved = subscriptionRepo.save(subscription);
        idempotencyStore.save(command.idempotencyKey(), "SUBSCRIPTION",
                saved.getId(), toJson(saved));

        eventPublisher.publish(new TierUpgradedEvent(
                saved.getId(), saved.getUserId(), fromTierId, saved.getTierId()));

        log.info("Tier upgraded: subscriptionId={} from={} to={} upgradeCostCents={}",
                saved.getId(), fromTierId, saved.getTierId(), upgradeCost);
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
