package com.sentinel.identity.application.service;

import com.sentinel.identity.adapter.out.persistence.IdentityVerificationRepository;
import com.sentinel.identity.domain.model.IdentityVerification;
import com.sentinel.identity.domain.model.VerificationDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Perpetual KYC.
 *
 * <p>Periodic review as an annual batch is a compliance ritual: it rechecks
 * thousands of customers who have not changed and misses the one who became
 * risky in March. This service does the opposite — it surfaces reviews when
 * the review date arrives, and lets risk events pull a review forward.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PerpetualKycService {

    private final IdentityVerificationRepository repository;

    /** Identities whose review date has passed. The work queue, not a report. */
    @Transactional(readOnly = true)
    public List<IdentityVerification> due() {
        return repository.findByNextReviewAtBeforeAndDecisionNot(Instant.now(), VerificationDecision.REJECTED);
    }

    /**
     * Pulls a review forward because something changed: a new device, a
     * sanctions-list update, a mule link, a sharp change in payment behaviour.
     */
    @Transactional
    public int triggerReview(UUID orgId, String subjectId, String trigger) {
        List<IdentityVerification> history =
                repository.findByOrganizationIdAndSubjectIdOrderByCreatedAtDesc(orgId, subjectId);
        if (history.isEmpty()) return 0;

        IdentityVerification latest = history.get(0);
        latest.setNextReviewAt(Instant.now());
        repository.save(latest);
        log.info("Perpetual KYC review pulled forward for {} — trigger: {}", subjectId, trigger);
        return 1;
    }

    @Scheduled(cron = "${sentinel.kyc.review-cron:0 0 2 * * *}")
    public void reportDueReviews() {
        int count = due().size();
        if (count > 0) {
            log.info("Perpetual KYC: {} identities are due for re-verification", count);
        }
    }
}
