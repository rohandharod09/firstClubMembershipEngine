package com.firstclub.membership.scheduler;

import com.firstclub.membership.infrastructure.persistence.repository.IdempotencyRecordJpaRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Purges expired rows from the idempotency_records table.
 *
 * Without this job, expired rows accumulate indefinitely. They are already
 * "soft-ignored" at read time (JpaIdempotencyStore filters them out), but
 * they still consume storage and slow down index scans over time.
 *
 * Runs once per hour across all nodes. ShedLock ensures exactly one node
 * executes the DELETE even in a multi-instance deployment.
 */
@Component
public class IdempotencyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

    private final IdempotencyRecordJpaRepository repo;

    public IdempotencyCleanupJob(IdempotencyRecordJpaRepository repo) {
        this.repo = repo;
    }

    @Scheduled(cron = "${membership.scheduler.idempotency-cleanup-cron:0 0 * * * *}")
    @SchedulerLock(name = "IdempotencyCleanupJob",
            lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
    @Transactional
    public void purgeExpired() {
        int deleted = repo.deleteExpiredRecords(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired idempotency record(s)", deleted);
        }
    }
}
