package com.sentinel.identity.application.service;

import com.sentinel.identity.adapter.out.persistence.IdentityVerificationRepository;
import com.sentinel.identity.application.dto.IdentityVerificationResponse;
import com.sentinel.identity.application.dto.VerifyIdentityRequest;
import com.sentinel.identity.domain.model.IdentityVerification;
import com.sentinel.identity.domain.model.SanctionsHit;
import com.sentinel.identity.domain.model.VerificationDecision;
import com.sentinel.identity.domain.model.VerificationType;
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
 * Orchestrates the identity decision across every evidence source.
 *
 * <p>The overall score takes the <b>maximum</b> of the weighted blend and the
 * strongest single dimension. A forged document is disqualifying on its own,
 * and averaging a 90 document score against five clean dimensions would
 * produce a number in the twenties and approve the applicant. The same
 * reasoning as the transaction risk engine's evidence floor, applied to
 * onboarding.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityVerificationService {

    private static final double DOCUMENT_WEIGHT = 0.26;
    private static final double LIVENESS_WEIGHT = 0.16;
    private static final double DEEPFAKE_WEIGHT = 0.22;
    private static final double SYNTHETIC_WEIGHT = 0.20;
    private static final double FOOTPRINT_WEIGHT = 0.06;
    private static final double TELCO_WEIGHT = 0.10;

    private final IdentityVerificationRepository repository;
    private final DocumentForensicsService documentForensics;
    private final BiometricIntegrityService biometricIntegrity;
    private final SyntheticIdentityService syntheticIdentity;
    private final DigitalFootprintService digitalFootprint;
    private final TelcoIntelligenceService telcoIntelligence;
    private final SanctionsScreeningService sanctionsScreening;

    @Transactional
    public IdentityVerificationResponse verify(UUID orgId, VerifyIdentityRequest request) {
        List<String> reasons = new ArrayList<>();

        int documentScore = documentForensics.score(request.getDocument(), reasons);
        BiometricIntegrityService.Result biometrics = biometricIntegrity.score(request.getBiometrics(), reasons);
        int syntheticScore = syntheticIdentity.score(request, reasons);
        int footprintScore = digitalFootprint.score(request.getFootprint(), reasons);
        int telcoScore = telcoIntelligence.score(request.getTelco(), reasons);

        int blended = (int) Math.round(
                documentScore * DOCUMENT_WEIGHT
                        + biometrics.getLivenessScore() * LIVENESS_WEIGHT
                        + biometrics.getDeepfakeScore() * DEEPFAKE_WEIGHT
                        + syntheticScore * SYNTHETIC_WEIGHT
                        + footprintScore * FOOTPRINT_WEIGHT
                        + telcoScore * TELCO_WEIGHT);

        int strongest = Math.max(documentScore,
                Math.max(biometrics.getDeepfakeScore(), Math.max(syntheticScore, biometrics.getLivenessScore())));
        // A single disqualifying finding sets the floor at 85% of its own score.
        int riskScore = Math.min(100, Math.max(blended, (int) Math.round(strongest * 0.85)));

        VerificationType type = request.getType() == null ? VerificationType.ONBOARDING : request.getType();

        IdentityVerification verification = repository.save(IdentityVerification.builder()
                .organizationId(orgId)
                .subjectId(request.getSubjectId())
                .subjectName(request.getFullName())
                .type(type)
                .decision(VerificationDecision.MANUAL_REVIEW)
                .riskScore(riskScore)
                .documentScore(documentScore)
                .livenessScore(biometrics.getLivenessScore())
                .deepfakeScore(biometrics.getDeepfakeScore())
                .syntheticScore(syntheticScore)
                .footprintScore(footprintScore)
                .telcoScore(telcoScore)
                .sanctionsHit(false)
                .reasons(reasons.isEmpty() ? List.of("No adverse identity signals") : List.copyOf(reasons))
                .imposedLimits(List.of())
                .build());

        List<SanctionsHit> hits = sanctionsScreening.screen(orgId, request.getSubjectId(), request.getFullName(),
                request.getDateOfBirth(), request.getCountry(), verification.getId());
        boolean openHit = hits.stream().anyMatch(h -> "OPEN".equals(h.getStatus()));

        if (openHit) {
            reasons.add("Unresolved sanctions or PEP match requires review");
        }

        VerificationDecision decision = decide(riskScore, openHit);
        List<String> limits = limitsFor(decision, riskScore, syntheticScore);

        verification.setSanctionsHit(openHit);
        verification.setDecision(decision);
        verification.setReasons(List.copyOf(reasons));
        verification.setImposedLimits(limits);
        verification.setNextReviewAt(nextReview(decision, riskScore));
        repository.save(verification);

        log.info("Identity {} decided {} (risk {}) doc={} live={} deepfake={} synthetic={}",
                request.getSubjectId(), decision, riskScore, documentScore,
                biometrics.getLivenessScore(), biometrics.getDeepfakeScore(), syntheticScore);

        return IdentityVerificationResponse.from(verification, hits);
    }

    private VerificationDecision decide(int riskScore, boolean openSanctionsHit) {
        if (openSanctionsHit) return VerificationDecision.MANUAL_REVIEW;
        if (riskScore >= 80) return VerificationDecision.REJECTED;
        if (riskScore >= 60) return VerificationDecision.MANUAL_REVIEW;
        if (riskScore >= 40) return VerificationDecision.ADDITIONAL_EVIDENCE_REQUIRED;
        if (riskScore >= 25) return VerificationDecision.APPROVED_WITH_LIMITS;
        return VerificationDecision.APPROVED;
    }

    /**
     * Limits instead of refusal. A moderately risky applicant who is genuine
     * becomes a customer, and a synthetic one hits a ceiling long before the
     * bust-out is worth attempting.
     */
    private List<String> limitsFor(VerificationDecision decision, int riskScore, int syntheticScore) {
        if (decision != VerificationDecision.APPROVED_WITH_LIMITS) return List.of();
        List<String> limits = new ArrayList<>();
        limits.add("Outbound payments capped at 1,000 per day for 30 days");
        if (syntheticScore >= 30) {
            limits.add("No credit products until the credit file matures");
        }
        if (riskScore >= 35) {
            limits.add("Step-up authentication required on every new payee");
        }
        return limits;
    }

    /**
     * Perpetual KYC: the review date is a function of risk, not of the calendar.
     * A clean retail customer is revisited in three years; a borderline one in
     * three months.
     */
    private Instant nextReview(VerificationDecision decision, int riskScore) {
        if (decision == VerificationDecision.REJECTED) return null;
        int days = riskScore >= 50 ? 90 : riskScore >= 25 ? 365 : 1095;
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }

    @Transactional
    public IdentityVerification override(UUID orgId, UUID verificationId, VerificationDecision decision, UUID reviewerId) {
        IdentityVerification verification = repository.findById(verificationId)
                .filter(v -> v.getOrganizationId().equals(orgId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown verification " + verificationId));
        verification.setDecision(decision);
        verification.setReviewedBy(reviewerId);
        verification.setNextReviewAt(nextReview(decision, verification.getRiskScore()));
        return repository.save(verification);
    }
}
