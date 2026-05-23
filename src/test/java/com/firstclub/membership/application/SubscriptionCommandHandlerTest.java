package com.firstclub.membership.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.firstclub.membership.application.command.CancelSubscriptionCommand;
import com.firstclub.membership.application.command.SubscribeCommand;
import com.firstclub.membership.application.handler.SubscriptionCommandHandler;
import com.firstclub.membership.application.port.outbound.*;
import com.firstclub.membership.common.exception.EligibilityNotMetException;
import com.firstclub.membership.common.exception.PaymentFailedException;
import com.firstclub.membership.common.exception.SubscriptionConflictException;
import com.firstclub.membership.common.util.Clock;
import com.firstclub.membership.domain.model.*;
import com.firstclub.membership.domain.rule.CompositeEligibilityEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionCommandHandler")
class SubscriptionCommandHandlerTest {

    @Mock private SubscriptionRepository subscriptionRepo;
    @Mock private PlanRepository planRepo;
    @Mock private TierRepository tierRepo;
    @Mock private PaymentGateway paymentGateway;
    @Mock private EventPublisher eventPublisher;
    @Mock private IdempotencyStore idempotencyStore;
    @Mock private CompositeEligibilityEvaluator eligibilityEvaluator;
    @Mock private Clock clock;

    private SubscriptionCommandHandler handler;
    private ObjectMapper objectMapper;

    private final UUID userId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private final UUID tierId = UUID.randomUUID();
    private final String idempotencyKey = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        handler = new SubscriptionCommandHandler(
                subscriptionRepo, planRepo, tierRepo, paymentGateway,
                eventPublisher, idempotencyStore, eligibilityEvaluator, objectMapper, clock);
    }

    private MembershipPlan stubPlan() {
        MembershipTier tier = new MembershipTier(tierId, planId, "Silver", 1,
                0L, true, List.of(), List.of());
        return new MembershipPlan(planId, "Monthly", 30, 9900L, "INR", true,
                List.of(tier));
    }

    private MembershipTier stubTier() {
        return new MembershipTier(tierId, planId, "Silver", 1, 0L, true,
                List.of(), List.of());
    }

    @Test
    @DisplayName("subscribe: success creates active subscription")
    void subscribeSuccess() {
        when(clock.now()).thenReturn(Instant.now());
        when(idempotencyStore.findByKey(idempotencyKey)).thenReturn(Optional.empty());
        when(subscriptionRepo.findActiveByUserId(userId)).thenReturn(Optional.empty());
        when(planRepo.findById(planId)).thenReturn(Optional.of(stubPlan()));
        when(tierRepo.findActiveById(tierId)).thenReturn(Optional.of(stubTier()));
        when(paymentGateway.charge(any(), anyLong(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(true, "TXN123", null));

        UserSubscription saved = buildActiveSub();
        when(subscriptionRepo.save(any())).thenReturn(saved);

        var command = new SubscribeCommand(userId, planId, tierId, true,
                null, 0, 0, idempotencyKey);
        var result = handler.subscribe(command);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(eventPublisher).publish(any());
        verify(idempotencyStore).save(eq(idempotencyKey), eq("SUBSCRIPTION"), any(), any());
    }

    @Test
    @DisplayName("subscribe: throws SubscriptionConflictException when active subscription exists")
    void subscribeConflict() {
        when(idempotencyStore.findByKey(idempotencyKey)).thenReturn(Optional.empty());
        when(subscriptionRepo.findActiveByUserId(userId))
                .thenReturn(Optional.of(buildActiveSub()));

        var command = new SubscribeCommand(userId, planId, tierId, true,
                null, 0, 0, idempotencyKey);

        assertThatThrownBy(() -> handler.subscribe(command))
                .isInstanceOf(SubscriptionConflictException.class);
    }

    @Test
    @DisplayName("subscribe: throws PaymentFailedException when payment fails")
    void subscribePaymentFails() {
        when(clock.now()).thenReturn(Instant.now());
        when(idempotencyStore.findByKey(idempotencyKey)).thenReturn(Optional.empty());
        when(subscriptionRepo.findActiveByUserId(userId)).thenReturn(Optional.empty());
        when(planRepo.findById(planId)).thenReturn(Optional.of(stubPlan()));
        when(tierRepo.findActiveById(tierId)).thenReturn(Optional.of(stubTier()));
        when(paymentGateway.charge(any(), anyLong(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(false, null, "Card declined"));

        var command = new SubscribeCommand(userId, planId, tierId, true,
                null, 0, 0, idempotencyKey);

        assertThatThrownBy(() -> handler.subscribe(command))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("Card declined");
    }

    @Test
    @DisplayName("cancel: successfully cancels an active subscription")
    void cancelSuccess() {
        UUID subId = UUID.randomUUID();
        when(idempotencyStore.findByKey(idempotencyKey)).thenReturn(Optional.empty());

        UserSubscription sub = buildActiveSub();
        when(subscriptionRepo.findById(subId)).thenReturn(Optional.of(sub));

        UserSubscription cancelled = buildCancelledSub(sub.getId(), userId);
        when(subscriptionRepo.save(any())).thenReturn(cancelled);

        var command = new CancelSubscriptionCommand(subId, userId, "No longer needed",
                idempotencyKey);
        var result = handler.cancel(command);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        verify(eventPublisher).publish(any());
    }

    private UserSubscription buildActiveSub() {
        UserSubscription sub = UserSubscription.create(
                userId, planId, tierId, Instant.now(),
                Instant.now().plusSeconds(30 * 86400L), true);
        sub.activate();
        return sub;
    }

    private UserSubscription buildCancelledSub(UUID id, UUID uid) {
        UserSubscription sub = UserSubscription.create(
                uid, planId, tierId, Instant.now(),
                Instant.now().plusSeconds(30 * 86400L), true);
        sub.activate();
        sub.cancel();
        return sub;
    }
}
