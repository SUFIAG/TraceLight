package com.sentinel.autonomy.application.service;

import com.sentinel.ai.adapter.out.llm.LlmClient;
import com.sentinel.alert.adapter.out.persistence.AlertRepository;
import com.sentinel.alert.domain.model.AlertSeverity;
import com.sentinel.alert.domain.model.AlertStatus;
import com.sentinel.alert.domain.model.FraudAlert;
import com.sentinel.autonomy.adapter.out.persistence.AgentRunRepository;
import com.sentinel.autonomy.domain.model.AgentActionType;
import com.sentinel.autonomy.domain.model.AgentRun;
import com.sentinel.autonomy.domain.model.AgentVerdict;
import com.sentinel.fraud.risk.adapter.out.persistence.RiskScoreRepository;
import com.sentinel.fraud.risk.domain.model.RiskScore;
import com.sentinel.graph.adapter.out.persistence.EntityLinkRepository;
import com.sentinel.mule.application.service.BeneficiaryRiskService;
import com.sentinel.transaction.adapter.out.persistence.TransactionRepository;
import com.sentinel.transaction.domain.model.Transaction;
import com.sentinel.transaction.domain.model.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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
 * The autonomous investigator.
 *
 * <p>It does what a tier-one analyst does on an alert: pull the transaction,
 * read the risk breakdown, check the customer's own history, look at the
 * destination account, check whether the customer sits in a known network,
 * then write a verdict. The difference is that it does it in milliseconds and
 * never gets bored on the four-hundredth identical false positive.
 *
 * <p>Two design choices are load-bearing:
 *
 * <ul>
 *   <li><b>Evidence first, verdict second.</b> The agent collects facts with
 *       sources, scores them deterministically, and only then optionally asks
 *       a language model to write the prose. The verdict never depends on the
 *       model, so the platform behaves identically when the model is off.</li>
 *   <li><b>Asymmetric confidence.</b> Closing an alert needs much stronger
 *       evidence than escalating one. Escalation costs an analyst a few
 *       minutes; a wrong closure is a missed fraud nobody will ever look at
 *       again.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TriageAgentService {

    private static final String AGENT_NAME = "TRIAGE";
    /** A customer with at least this many prior transactions has a baseline worth trusting. */
    private static final int ESTABLISHED_HISTORY = 20;

    private final AlertRepository alertRepository;
    private final TransactionRepository transactionRepository;
    private final RiskScoreRepository riskScoreRepository;
    private final BeneficiaryRiskService beneficiaryRiskService;
    private final EntityLinkRepository entityLinkRepository;
    private final AgentRunRepository agentRunRepository;
    private final AutonomyPolicyService autonomyPolicyService;
    private final LlmClient llmClient;

    /** Works the open alert queue and returns what it did. */
    @Transactional
    public List<AgentRun> triageQueue(UUID orgId, int limit) {
        List<FraudAlert> open = alertRepository
                .findByOrganizationIdAndStatusOrderByCreatedAtDesc(orgId, AlertStatus.OPEN, PageRequest.of(0, limit))
                .getContent();

        List<AgentRun> runs = new ArrayList<>();
        for (FraudAlert alert : open) {
            runs.add(triage(orgId, alert));
        }

        long closed = runs.stream().filter(AgentRun::isActionExecuted).count();
        log.info("Triage agent processed {} alerts for org {}, resolved {} without a human",
                runs.size(), orgId, closed);
        return runs;
    }

    @Transactional
    public AgentRun triage(UUID orgId, UUID alertId) {
        FraudAlert alert = alertRepository.findById(alertId)
                .filter(a -> a.getOrganizationId().equals(orgId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown alert " + alertId));
        return triage(orgId, alert);
    }

    private AgentRun triage(UUID orgId, FraudAlert alert) {
        long start = System.currentTimeMillis();
        List<String> evidence = new ArrayList<>();

        Optional<Transaction> maybeTransaction = transactionRepository.findById(alert.getTransactionId());
        if (maybeTransaction.isEmpty()) {
            return record(orgId, alert, AgentVerdict.INSUFFICIENT_EVIDENCE, 0, null,
                    false, "Transaction behind the alert no longer exists",
                    "The alert references a transaction that cannot be loaded.",
                    List.of("Transaction " + alert.getTransactionId() + " not found"), start);
        }

        Transaction transaction = maybeTransaction.get();
        RiskScore riskScore = riskScoreRepository.findByTransactionId(transaction.getId()).orElse(null);

        int exoneratingWeight = 0;
        int incriminatingWeight = 0;

        /* --- The customer's own baseline --- */
        long historyCount = transactionRepository.countByOrganizationIdAndUserId(orgId, transaction.getUserId());
        BigDecimal averageAmount = transactionRepository.findAverageAmountByUserId(orgId, transaction.getUserId());
        evidence.add("Customer has " + historyCount + " prior transactions, averaging " + averageAmount);

        if (historyCount >= ESTABLISHED_HISTORY) {
            exoneratingWeight += 20;
            if (averageAmount.signum() > 0
                    && transaction.getAmount().compareTo(averageAmount.multiply(new BigDecimal("2"))) <= 0) {
                evidence.add("Amount is within twice the customer's own average — ordinary for this customer");
                exoneratingWeight += 25;
            } else {
                evidence.add("Amount is well above the customer's own average");
                incriminatingWeight += 20;
            }
        } else {
            evidence.add("Thin transaction history: the customer baseline is not yet reliable");
            incriminatingWeight += 10;
        }

        /* --- The risk breakdown --- */
        if (riskScore != null) {
            evidence.add("Risk score " + riskScore.getScore() + " (" + riskScore.getLevel() + ") — amount "
                    + riskScore.getAmountRisk() + ", velocity " + riskScore.getVelocityRisk()
                    + ", location " + riskScore.getLocationRisk() + ", device " + riskScore.getDeviceRisk());
            if (riskScore.getScore() >= 85) incriminatingWeight += 35;
            else if (riskScore.getScore() >= 70) incriminatingWeight += 20;
            else if (riskScore.getScore() < 65) exoneratingWeight += 15;
        }

        /* --- The destination account --- */
        int beneficiaryRisk = beneficiaryRiskService.riskOf(orgId, transaction.getMerchantId());
        if (beneficiaryRiskService.isConfirmedMule(orgId, transaction.getMerchantId())) {
            evidence.add("Destination " + transaction.getMerchantId() + " is a confirmed mule account");
            incriminatingWeight += 60;
        } else if (beneficiaryRisk >= 50) {
            evidence.add("Destination " + transaction.getMerchantId() + " scores " + beneficiaryRisk + " for mule behaviour");
            incriminatingWeight += 25;
        } else {
            evidence.add("Destination account shows no mule characteristics");
            exoneratingWeight += 10;
        }

        /* --- Network position --- */
        int links = entityLinkRepository.findNeighbours(orgId, transaction.getUserId()).size();
        if (links >= 5) {
            evidence.add("Customer is linked to " + links + " other accounts in the fraud graph");
            incriminatingWeight += 25;
        } else if (links > 0) {
            evidence.add("Customer has " + links + " graph links, which is unremarkable");
        }

        /* --- Repeat behaviour --- */
        long recentFlagged = transactionRepository.countByOrganizationIdAndStatusSince(
                orgId, TransactionStatus.FLAGGED, Instant.now().minus(30, ChronoUnit.DAYS));
        evidence.add("Organisation flagged " + recentFlagged + " transactions in the last 30 days");

        /* --- Verdict --- */
        int net = incriminatingWeight - exoneratingWeight;
        AgentVerdict verdict = verdictOf(net, alert.getSeverity());
        int confidence = confidenceOf(net, incriminatingWeight, exoneratingWeight);

        AgentActionType proposed = verdict == AgentVerdict.BENIGN
                ? AgentActionType.CLOSE_ALERT
                : AgentActionType.ESCALATE_ALERT;

        String rationale = buildRationale(verdict, evidence, transaction, riskScore);

        AutonomyPolicyService.Decision decision = autonomyPolicyService.evaluate(
                orgId, proposed, confidence, transaction.getAmount(),
                riskScore == null ? null : riskScore.getScore());

        boolean executed = false;
        if (decision.allowed()) {
            if (proposed == AgentActionType.CLOSE_ALERT) {
                alert.setStatus(AlertStatus.FALSE_POSITIVE);
                alert.setReviewedAt(Instant.now());
                alertRepository.save(alert);
                executed = true;
            } else if (proposed == AgentActionType.ESCALATE_ALERT) {
                alert.setStatus(AlertStatus.INVESTIGATING);
                alertRepository.save(alert);
                executed = true;
            }
        }

        return record(orgId, alert, verdict, confidence, proposed, executed,
                decision.allowed() ? null : decision.reason(), rationale, evidence, start);
    }

    /**
     * BENIGN requires the exonerating evidence to clearly outweigh the
     * incriminating evidence, and a CRITICAL alert is never auto-closed on
     * weight alone — the severity was assigned for a reason.
     */
    private AgentVerdict verdictOf(int net, AlertSeverity severity) {
        if (severity == AlertSeverity.CRITICAL && net > -40) {
            return AgentVerdict.SUSPICIOUS;
        }
        if (net <= -25) return AgentVerdict.BENIGN;
        if (net >= 60) return AgentVerdict.FRAUDULENT;
        if (net >= 20) return AgentVerdict.SUSPICIOUS;
        return AgentVerdict.NEEDS_REVIEW;
    }

    private int confidenceOf(int net, int incriminating, int exonerating) {
        int total = incriminating + exonerating;
        if (total == 0) return 30;
        // Confidence is how lopsided the evidence is, not how much there is.
        int margin = Math.abs(net) * 100 / total;
        return Math.max(25, Math.min(97, 40 + margin / 2 + Math.min(20, total / 8)));
    }

    private String buildRationale(AgentVerdict verdict, List<String> evidence,
                                  Transaction transaction, RiskScore riskScore) {
        StringBuilder deterministic = new StringBuilder();
        deterministic.append("Verdict: ").append(verdict).append(". ");
        deterministic.append("Transaction of ").append(transaction.getAmount()).append(' ')
                .append(transaction.getCurrency()).append(" from ").append(transaction.getUserId())
                .append(" to ").append(transaction.getMerchantId()).append(". ");
        if (riskScore != null) {
            deterministic.append("Scored ").append(riskScore.getScore())
                    .append(" (").append(riskScore.getLevel()).append("). ");
        }
        deterministic.append("Findings: ").append(String.join("; ", evidence)).append('.');

        if (!llmClient.isEnabled()) {
            return deterministic.toString();
        }

        String system = """
                You are a fraud analyst writing the closing note on an alert.
                Use only the evidence given. Do not invent facts, names, or figures.
                Write three to five sentences: what happened, what the evidence shows,
                and why the verdict follows. Plain English, no hedging.
                """;
        return llmClient.complete(system, deterministic.toString())
                .map(text -> text + "\n\n[Evidence: " + String.join(" | ", evidence) + "]")
                .orElse(deterministic.toString());
    }

    private AgentRun record(UUID orgId, FraudAlert alert, AgentVerdict verdict, int confidence,
                            AgentActionType action, boolean executed, String suppressedReason,
                            String rationale, List<String> evidence, long start) {
        return agentRunRepository.save(AgentRun.builder()
                .organizationId(orgId)
                .agentName(AGENT_NAME)
                .targetType("ALERT")
                .targetId(alert.getId().toString())
                .verdict(verdict)
                .confidence(confidence)
                .proposedAction(action)
                .actionExecuted(executed)
                .suppressedReason(suppressedReason)
                .rationale(rationale)
                .evidence(evidence)
                .durationMs(System.currentTimeMillis() - start)
                .llmAssisted(llmClient.isEnabled())
                .build());
    }

    /**
     * Records whether a human agreed with the agent. This is the only honest
     * measure of whether autonomy should be widened, and it is also the label
     * set that future tuning learns from.
     */
    @Transactional
    public AgentRun review(UUID orgId, UUID runId, boolean agreed, UUID reviewerId) {
        AgentRun run = agentRunRepository.findByIdAndOrganizationId(runId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown agent run " + runId));
        run.setHumanAgreed(agreed);
        run.setReviewedBy(reviewerId);
        return agentRunRepository.save(run);
    }
}
