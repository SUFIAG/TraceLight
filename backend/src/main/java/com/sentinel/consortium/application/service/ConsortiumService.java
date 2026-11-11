package com.sentinel.consortium.application.service;

import com.sentinel.consortium.adapter.out.persistence.ConsortiumContributionRepository;
import com.sentinel.consortium.adapter.out.persistence.ConsortiumSignalRepository;
import com.sentinel.consortium.domain.model.ConsortiumContribution;
import com.sentinel.consortium.domain.model.ConsortiumSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Cross-institution intelligence sharing without sharing identities.
 *
 * <p>Three properties make this acceptable to a bank's legal and privacy
 * teams, and all three are enforced in code rather than in a contract:
 *
 * <ol>
 *   <li><b>No raw identifiers leave the contributor.</b> Everything is keyed
 *       hashed before storage, so the shared table holds no account numbers,
 *       no emails, and no device identifiers.</li>
 *   <li><b>No contributor is identifiable from a lookup.</b> Attribution
 *       lives in a separate table used only for counting and withdrawal.</li>
 *   <li><b>A signal is only shared once two institutions agree.</b> One
 *       bank's suspicion stays that bank's suspicion.</li>
 * </ol>
 *
 * <p>Signals also expire. Fraud intelligence has a short half-life, and a
 * device that was compromised two years ago tells you about its owner's bad
 * luck, not about today's risk.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConsortiumService {

    /** A signal must be reported by this many institutions before it is readable. */
    private static final int MIN_CONTRIBUTORS = 2;
    private static final int DEFAULT_TTL_DAYS = 365;

    private final ConsortiumSignalRepository signalRepository;
    private final ConsortiumContributionRepository contributionRepository;

    /**
     * Platform-wide pepper. Must be set in production: with a predictable
     * value, the hashes can be enumerated and the privacy guarantee is void.
     */
    @Value("${sentinel.consortium.pepper:sentinel-development-pepper-replace-in-production}")
    private String pepper;

    @Transactional
    public ConsortiumSignal contribute(UUID orgId, String identifier, String identifierType,
                                       String signalType, int severity) {
        String hashed = hash(identifier, identifierType);

        ConsortiumSignal signal = signalRepository
                .findByHashedIdentifierAndSignalType(hashed, signalType)
                .orElseGet(() -> ConsortiumSignal.builder()
                        .hashedIdentifier(hashed)
                        .identifierType(identifierType.toUpperCase(Locale.ROOT))
                        .signalType(signalType.toUpperCase(Locale.ROOT))
                        .severity(severity)
                        .contributorCount(0)
                        .sightingCount(0)
                        .firstSeen(Instant.now())
                        .lastSeen(Instant.now())
                        .build());

        signal.setSightingCount(signal.getSightingCount() + 1);
        signal.setLastSeen(Instant.now());
        signal.setExpiresAt(Instant.now().plus(DEFAULT_TTL_DAYS, ChronoUnit.DAYS));
        final ConsortiumSignal persisted = signalRepository.save(signal);
        signal = persisted;

        ConsortiumContribution contribution = contributionRepository
                .findBySignalIdAndOrganizationId(persisted.getId(), orgId)
                .orElseGet(() -> ConsortiumContribution.builder()
                        .signalId(persisted.getId())
                        .organizationId(orgId)
                        .reportCount(0)
                        .build());
        contribution.setSeverity(severity);
        contribution.setReportCount(contribution.getReportCount() + 1);
        contribution.setLastReportedAt(Instant.now());
        contributionRepository.save(contribution);

        int contributors = (int) contributionRepository.countBySignalId(signal.getId());
        signal.setContributorCount(contributors);
        // Severity converges on the consensus rather than following the loudest reporter.
        signal.setSeverity((signal.getSeverity() + severity) / 2);

        ConsortiumSignal saved = signalRepository.save(signal);
        log.debug("Consortium signal {} now has {} contributors", signalType, contributors);
        return saved;
    }

    /**
     * Looks up an identifier. Returns only signals that cleared the
     * k-anonymity gate and have not expired.
     */
    @Transactional(readOnly = true)
    public List<Match> query(String identifier, String identifierType) {
        String hashed = hash(identifier, identifierType);
        List<Match> matches = new ArrayList<>();

        for (ConsortiumSignal signal : signalRepository.findByHashedIdentifier(hashed)) {
            if (signal.getContributorCount() < MIN_CONTRIBUTORS) continue;
            if (signal.getExpiresAt() != null && signal.getExpiresAt().isBefore(Instant.now())) continue;
            matches.add(new Match(
                    signal.getSignalType(),
                    signal.getSeverity(),
                    signal.getContributorCount(),
                    signal.getSightingCount(),
                    signal.getFirstSeen(),
                    signal.getLastSeen()));
        }
        return matches;
    }

    /** The highest severity across all matching signals, for inline scoring. */
    @Transactional(readOnly = true)
    public int riskOf(String identifier, String identifierType) {
        return query(identifier, identifierType).stream()
                .mapToInt(Match::severity)
                .max()
                .orElse(0);
    }

    /** Lets a contributor withdraw everything it ever reported. */
    @Transactional
    public void withdraw(UUID orgId) {
        contributionRepository.deleteByOrganizationId(orgId);
        log.warn("Organization {} withdrew all consortium contributions", orgId);
    }

    /**
     * HMAC rather than a bare digest. The identifier space for an account
     * number or a phone number is small enough to enumerate against a plain
     * SHA-256, which would turn the shared table into a lookup directory.
     */
    private String hash(String identifier, String identifierType) {
        try {
            String normalised = identifierType.toUpperCase(Locale.ROOT) + ":"
                    + identifier.trim().toLowerCase(Locale.ROOT);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(normalised.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Consortium hashing is unavailable", e);
        }
    }

    /** What a lookup returns: the fact of the signal, never its source. */
    public record Match(String signalType, int severity, int contributorCount,
                        int sightingCount, Instant firstSeen, Instant lastSeen) {
    }
}
