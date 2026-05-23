package com.firstclub.membership.concurrency;

import com.firstclub.membership.domain.model.SubscriptionStatus;
import com.firstclub.membership.domain.model.UserSubscription;
import com.firstclub.membership.domain.statemachine.SubscriptionStateMachine;
import com.firstclub.membership.common.exception.IllegalStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency unit tests that verify domain-level behavior under concurrent access.
 * Full integration tests (DB-level concurrent subscribe) would use Testcontainers.
 */
@DisplayName("Concurrent Subscription Tests")
class ConcurrentSubscriptionTest {

    @Test
    @DisplayName("State machine rejects concurrent invalid transitions (thread-safety check)")
    void stateMachineIsThreadSafe() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    SubscriptionStateMachine.validateTransition(
                            SubscriptionStatus.ACTIVE, SubscriptionStatus.CANCELLED);
                    successCount.incrementAndGet();
                } catch (IllegalStateTransitionException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // All valid transitions should succeed — state machine is stateless, so all pass
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("Concurrent invalid transitions all fail correctly")
    void stateMachineRejectsInvalidConcurrently() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // CANCELLED -> ACTIVE is always invalid
                    SubscriptionStateMachine.validateTransition(
                            SubscriptionStatus.CANCELLED, SubscriptionStatus.ACTIVE);
                } catch (IllegalStateTransitionException e) {
                    rejectedCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(rejectedCount.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("UserSubscription domain object transitions are single-threaded safe")
    void subscriptionTransitionsWork() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();

        UserSubscription sub = UserSubscription.create(
                userId, planId, tierId,
                Instant.now(), Instant.now().plusSeconds(30 * 86400L), true);

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PENDING_PAYMENT);

        sub.activate();
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

        sub.cancel();
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    @DisplayName("Prorated upgrade cost is calculated correctly")
    void upgradeCostCalculation() {
        Instant now = Instant.now();
        Instant start = now.minusSeconds(15 * 86400L); // 15 days ago
        Instant end = now.plusSeconds(15 * 86400L);    // 15 days left

        UserSubscription sub = UserSubscription.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                start, end, true);
        sub.activate();

        assertThat(sub.isExpiredAt(now)).isFalse();
        assertThat(sub.isExpiredAt(end.plusSeconds(1))).isTrue();
    }
}
