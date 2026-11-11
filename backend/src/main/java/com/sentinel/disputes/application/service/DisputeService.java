package com.sentinel.disputes.application.service;

import com.sentinel.ai.adapter.out.llm.LlmClient;
import com.sentinel.behavioral.device.adapter.out.persistence.DeviceRepository;
import com.sentinel.common.exception.BusinessException;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.disputes.adapter.out.persistence.DisputeRepository;
import com.sentinel.disputes.domain.model.Dispute;
import com.sentinel.disputes.domain.model.DisputeStatus;
import com.sentinel.fraud.risk.adapter.out.persistence.RiskScoreRepository;
import com.sentinel.transaction.adapter.out.persistence.TransactionRepository;
import com.sentinel.transaction.domain.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Chargeback handling, end to end.
 *
 * <p>Two things make disputes expensive. The first is evidence assembly: the
 * facts that win a representment — device, location, prior relationship,
 * delivery, the customer's own history — live in five systems and are
 * compiled by hand. The second is that nobody has time to compile them, so
 * institutions accept liability on cases they would have won.
 *
 * <p>This service assembles the evidence automatically, estimates whether the
 * case is winnable, and drafts the representment. It also scores first-party
 * fraud, which is the part the cardholder's bank never looks at: a customer
 * with six disputes this year, on their own device, at a merchant they use
 * monthly, is not a victim.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeService {

    /** Below this amount, fighting costs more than conceding. */
    private static final BigDecimal AUTO_ACCEPT_THRESHOLD = new BigDecimal("25");
    /** Network response windows are commonly 30 days. */
    private static final int RESPONSE_WINDOW_DAYS = 30;

    private final DisputeRepository disputeRepository;
    private final TransactionRepository transactionRepository;
    private final RiskScoreRepository riskScoreRepository;
    private final DeviceRepository deviceRepository;
    private final LlmClient llmClient;

    @Transactional
    public Dispute intake(UUID orgId, UUID transactionId, String network, String reasonCode,
                          String reasonDescription, String externalReference) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .filter(t -> t.getOrganizationId().equals(orgId))
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId.toString()));

        List<String> evidence = new ArrayList<>();
        int firstPartyScore = scoreFirstPartyFraud(orgId, transaction, evidence);
        int winProbability = estimateWinProbability(orgId, transaction, firstPartyScore, evidence);

        Dispute dispute = disputeRepository.save(Dispute.builder()
                .organizationId(orgId)
                .transactionId(transactionId)
                .externalReference(externalReference)
                .userId(transaction.getUserId())
                .merchantId(transaction.getMerchantId())
                .network(network)
                .reasonCode(reasonCode)
                .reasonDescription(reasonDescription)
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .status(DisputeStatus.EVIDENCE_GATHERING)
                .firstPartyFraudScore(firstPartyScore)
                .winProbability(winProbability)
                .evidence(evidence)
                .aiDrafted(false)
                .respondBy(Instant.now().plus(RESPONSE_WINDOW_DAYS, ChronoUnit.DAYS))
                .build());

        // Conceding small cases is a decision, not an oversight, and it is recorded as one.
        if (transaction.getAmount().compareTo(AUTO_ACCEPT_THRESHOLD) < 0) {
            dispute.setStatus(DisputeStatus.ACCEPTED);
            List<String> withNote = new ArrayList<>(evidence);
            withNote.add("Accepted without contest: the amount is below the cost of representment");
            dispute.setEvidence(withNote);
            return disputeRepository.save(dispute);
        }

        log.info("Dispute intake for transaction {}: first-party score {}, win probability {}",
                transactionId, firstPartyScore, winProbability);
        return dispute;
    }

    /**
     * Friendly fraud scoring. Each signal is weak alone and strong together:
     * the customer's own device, a merchant they have used before, a history
     * of disputes, and a delay before complaining.
     */
    private int scoreFirstPartyFraud(UUID orgId, Transaction transaction, List<String> evidence) {
        int score = 0;

        List<Dispute> history = disputeRepository.findByOrganizationIdAndUserId(orgId, transaction.getUserId());
        if (history.size() >= 5) {
            evidence.add("Cardholder has filed " + history.size() + " disputes previously");
            score += 40;
        } else if (history.size() >= 2) {
            evidence.add("Cardholder has filed " + history.size() + " disputes previously");
            score += 20;
        }

        boolean knownDevice = transaction.getDeviceId() != null
                && deviceRepository.findByOrganizationIdAndUserIdOrderByLastSeenDesc(orgId, transaction.getUserId())
                .stream()
                .anyMatch(device -> transaction.getDeviceId().equals(device.getFingerprint()));
        if (knownDevice) {
            evidence.add("Payment was made from a device already enrolled on this account");
            score += 30;
        }

        long priorAtMerchant = transactionRepository
                .findByOrganizationIdAndUserId(orgId, transaction.getUserId()).stream()
                .filter(t -> t.getMerchantId().equals(transaction.getMerchantId()))
                .filter(t -> t.getTimestamp().isBefore(transaction.getTimestamp()))
                .count();
        if (priorAtMerchant >= 3) {
            evidence.add("Cardholder has paid this merchant " + priorAtMerchant + " times before");
            score += 30;
        } else if (priorAtMerchant >= 1) {
            evidence.add("Cardholder has paid this merchant before");
            score += 15;
        }

        Optional<Integer> risk = riskScoreRepository.findByTransactionId(transaction.getId())
                .map(r -> r.getScore());
        if (risk.isPresent() && risk.get() < 30) {
            evidence.add("The original transaction scored only " + risk.get() + " for fraud risk");
            score += 20;
        }

        return Math.min(100, score);
    }

    /**
     * Win probability is largely the inverse of the cardholder's claim being
     * genuine. Reason codes that turn on authorisation are winnable with
     * device evidence; service-quality codes rarely are.
     */
    private int estimateWinProbability(UUID orgId, Transaction transaction, int firstPartyScore,
                                       List<String> evidence) {
        int probability = 20 + (int) (firstPartyScore * 0.6);

        if (transaction.getDeviceId() != null) {
            probability += 10;
        }
        if (transaction.getIpAddress() != null) {
            probability += 5;
        }

        int capped = Math.min(95, Math.max(5, probability));
        evidence.add("Estimated probability of a successful representment: " + capped + "%");
        return capped;
    }

    /** Assembles the representment. Template first; the model only improves the prose. */
    @Transactional
    public Dispute draftRepresentment(UUID orgId, UUID disputeId) {
        Dispute dispute = load(orgId, disputeId);
        if (dispute.getStatus() == DisputeStatus.SUBMITTED || dispute.getStatus() == DisputeStatus.WON) {
            throw new BusinessException("ALREADY_SUBMITTED", "This dispute has already been submitted");
        }

        Transaction transaction = dispute.getTransactionId() == null ? null
                : transactionRepository.findById(dispute.getTransactionId()).orElse(null);

        StringBuilder template = new StringBuilder();
        template.append("REPRESENTMENT — ").append(dispute.getNetwork())
                .append(" reason code ").append(dispute.getReasonCode()).append("\n\n");
        template.append("DISPUTED TRANSACTION\n");
        template.append("Amount: ").append(dispute.getAmount()).append(' ').append(dispute.getCurrency()).append('\n');
        if (transaction != null) {
            template.append("Date: ").append(transaction.getTimestamp()).append('\n');
            template.append("Merchant: ").append(transaction.getMerchantId()).append('\n');
            if (transaction.getDeviceId() != null) {
                template.append("Device: ").append(transaction.getDeviceId()).append('\n');
            }
            if (transaction.getIpAddress() != null) {
                template.append("IP address: ").append(transaction.getIpAddress()).append('\n');
            }
            if (transaction.getCountry() != null) {
                template.append("Country: ").append(transaction.getCountry()).append('\n');
            }
        }

        template.append("\nEVIDENCE\n");
        for (String item : dispute.getEvidence()) {
            template.append("- ").append(item).append('\n');
        }

        template.append("\nPOSITION\n");
        template.append("The transaction was authorised by the cardholder. ")
                .append("The evidence above shows the payment originated from the cardholder's own device ")
                .append("and is consistent with their established behaviour. ")
                .append("We therefore contest this chargeback and request that liability be reassigned.\n");

        String narrative = template.toString();
        boolean aiDrafted = false;

        if (llmClient.isEnabled()) {
            String system = """
                    You write card scheme representment letters for an issuing bank.
                    Rewrite the draft into formal, concise prose for a scheme arbitrator.
                    Never add evidence that is not in the draft. Never remove a figure or a date.
                    Keep the section headings. No more than 400 words.
                    """;
            final String baseline = narrative;
            Optional<String> improved = llmClient.complete(system, baseline)
                    .filter(text -> text.length() >= baseline.length() * 0.6);
            if (improved.isPresent()) {
                narrative = improved.get();
                aiDrafted = true;
            }
        }

        dispute.setRepresentmentText(narrative);
        dispute.setAiDrafted(aiDrafted);
        dispute.setStatus(DisputeStatus.REPRESENTMENT_DRAFTED);
        return disputeRepository.save(dispute);
    }

    @Transactional
    public Dispute submit(UUID orgId, UUID disputeId) {
        Dispute dispute = load(orgId, disputeId);
        if (dispute.getRepresentmentText() == null || dispute.getRepresentmentText().isBlank()) {
            throw new BusinessException("NO_REPRESENTMENT", "Draft the representment before submitting");
        }
        dispute.setStatus(DisputeStatus.SUBMITTED);
        dispute.setSubmittedAt(Instant.now());
        return disputeRepository.save(dispute);
    }

    @Transactional
    public Dispute resolve(UUID orgId, UUID disputeId, DisputeStatus outcome, BigDecimal recovered) {
        Dispute dispute = load(orgId, disputeId);
        dispute.setStatus(outcome);
        dispute.setResolvedAt(Instant.now());
        dispute.setRecoveredAmount(outcome == DisputeStatus.WON
                ? (recovered == null ? dispute.getAmount() : recovered)
                : BigDecimal.ZERO);
        return disputeRepository.save(dispute);
    }

    /** Cases whose network deadline has passed without a submission. */
    @Transactional(readOnly = true)
    public List<Dispute> overdue(UUID orgId) {
        return disputeRepository.findByOrganizationIdAndRespondByBeforeAndStatusNotIn(
                orgId, Instant.now(),
                List.of(DisputeStatus.SUBMITTED, DisputeStatus.WON, DisputeStatus.LOST, DisputeStatus.ACCEPTED));
    }

    private Dispute load(UUID orgId, UUID disputeId) {
        return disputeRepository.findByIdAndOrganizationId(disputeId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute", disputeId.toString()));
    }
}
