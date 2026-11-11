package com.sentinel.scam.application.service;

import com.sentinel.scam.adapter.out.persistence.ScamSignalRepository;
import com.sentinel.scam.adapter.out.persistence.VulnerableCustomerRepository;
import com.sentinel.scam.domain.model.ScamSignal;
import com.sentinel.scam.domain.model.VulnerableCustomer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Maintains the adapted-protection register.
 *
 * <p>Two routes in: a customer or relationship manager declares it, or the
 * platform infers it from repeated coaching signals. Inferred flags carry a
 * review date, because an automatic judgement about a person's vulnerability
 * that nobody ever revisits is a judgement that ages badly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VulnerableCustomerService {

    /** Coaching assessments above this, repeated, suggest someone is being targeted. */
    private static final int COACHING_THRESHOLD = 60;
    private static final int REPEAT_THRESHOLD = 3;

    private final VulnerableCustomerRepository repository;
    private final ScamSignalRepository scamSignalRepository;

    @Transactional(readOnly = true)
    public List<VulnerableCustomer> list(UUID orgId) {
        return repository.findByOrganizationIdAndActiveTrue(orgId);
    }

    @Transactional
    public VulnerableCustomer declare(UUID orgId, String userId, String basis, int protectionLevel, List<String> evidence) {
        VulnerableCustomer record = repository.findByOrganizationIdAndUserIdAndActiveTrue(orgId, userId)
                .orElseGet(() -> VulnerableCustomer.builder()
                        .organizationId(orgId)
                        .userId(userId)
                        .build());
        record.setBasis(basis);
        record.setProtectionLevel(Math.max(1, Math.min(3, protectionLevel)));
        record.setEvidence(evidence == null ? List.of() : evidence);
        record.setActive(true);
        record.setReviewDue(Instant.now().plus(180, ChronoUnit.DAYS));
        return repository.save(record);
    }

    @Transactional
    public void clear(UUID orgId, String userId) {
        repository.findByOrganizationIdAndUserIdAndActiveTrue(orgId, userId).ifPresent(record -> {
            record.setActive(false);
            repository.save(record);
        });
    }

    /**
     * Infers protection from scam history. Called after an assessment, so a
     * customer who is repeatedly targeted gets stronger protection before the
     * attempt that finally succeeds rather than after it.
     */
    @Transactional
    public void reassess(UUID orgId, String userId) {
        List<ScamSignal> history = scamSignalRepository
                .findByOrganizationIdAndUserIdOrderByCreatedAtDesc(orgId, userId);

        long coached = history.stream().filter(s -> s.getCoachingScore() >= COACHING_THRESHOLD).count();
        if (coached < REPEAT_THRESHOLD) return;
        if (repository.findByOrganizationIdAndUserIdAndActiveTrue(orgId, userId).isPresent()) return;

        List<String> evidence = new ArrayList<>();
        evidence.add(coached + " payment attempts with strong coaching indicators");
        history.stream().limit(3).forEach(s ->
                evidence.add(s.getTypology().getLabel() + " suspected on " + s.getCreatedAt()));

        declare(orgId, userId, "COERCION_SIGNALS", 2, evidence);
        log.info("Customer {} added to adapted protection after {} coached attempts", userId, coached);
    }
}
