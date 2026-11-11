package com.sentinel.autonomy.application.service;

import com.sentinel.alert.adapter.out.persistence.AlertRepository;
import com.sentinel.alert.domain.model.AlertStatus;
import com.sentinel.alert.domain.model.FraudAlert;
import com.sentinel.autonomy.adapter.out.persistence.OutreachAttemptRepository;
import com.sentinel.autonomy.domain.model.OutreachAttempt;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.transaction.adapter.out.persistence.TransactionRepository;
import com.sentinel.transaction.domain.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Asks the customer.
 *
 * <p>For a large share of alerts the cheapest investigator is the account
 * holder: they know in one second what an analyst needs twenty minutes to
 * infer. The agent sends a specific, answerable question — never "unusual
 * activity detected" — and resolves the alert from the reply.
 *
 * <p>The message deliberately contains no link and asks for no credentials.
 * A fraud-prevention message that trains customers to tap links in messages
 * is a phishing campaign the bank runs against itself.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutreachAgentService {

    private static final int RESPONSE_WINDOW_HOURS = 4;

    private final OutreachAttemptRepository outreachRepository;
    private final AlertRepository alertRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public OutreachAttempt contact(UUID orgId, UUID alertId, String channel) {
        FraudAlert alert = alertRepository.findById(alertId)
                .filter(a -> a.getOrganizationId().equals(orgId))
                .orElseThrow(() -> new ResourceNotFoundException("FraudAlert", alertId.toString()));

        Transaction transaction = transactionRepository.findById(alert.getTransactionId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", alert.getTransactionId().toString()));

        OutreachAttempt attempt = outreachRepository.save(OutreachAttempt.builder()
                .organizationId(orgId)
                .userId(transaction.getUserId())
                .alertId(alertId)
                .transactionId(transaction.getId())
                .channel(channel == null ? "PUSH" : channel)
                .message(compose(transaction))
                .outcome("PENDING")
                .expiresAt(Instant.now().plus(RESPONSE_WINDOW_HOURS, ChronoUnit.HOURS))
                .build());

        log.info("Outreach {} sent to {} over {}", attempt.getId(), transaction.getUserId(), attempt.getChannel());
        return attempt;
    }

    /**
     * A question the customer can answer from memory, with the two facts they
     * would recognise: amount and merchant.
     */
    private String compose(Transaction transaction) {
        return "Did you make a payment of " + transaction.getAmount() + " " + transaction.getCurrency()
                + " to " + transaction.getMerchantId() + "? Reply YES or NO. "
                + "We will never ask you for a password, a one-time code, or to move money.";
    }

    /**
     * Applies the customer's answer. A confirmation closes the alert outright;
     * a denial escalates it and the transaction is treated as confirmed fraud.
     */
    @Transactional
    public OutreachAttempt recordResponse(UUID orgId, UUID attemptId, boolean confirmedGenuine, String responseText) {
        OutreachAttempt attempt = outreachRepository.findByIdAndOrganizationId(attemptId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("OutreachAttempt", attemptId.toString()));

        attempt.setOutcome(confirmedGenuine ? "CONFIRMED_GENUINE" : "REPORTED_FRAUD");
        attempt.setResponseText(responseText);
        attempt.setRespondedAt(Instant.now());

        if (attempt.getAlertId() != null) {
            alertRepository.findById(attempt.getAlertId()).ifPresent(alert -> {
                alert.setStatus(confirmedGenuine ? AlertStatus.FALSE_POSITIVE : AlertStatus.INVESTIGATING);
                alert.setReviewedAt(Instant.now());
                alertRepository.save(alert);
            });
        }

        log.info("Outreach {} resolved as {}", attemptId, attempt.getOutcome());
        return outreachRepository.save(attempt);
    }

    /**
     * Silence is not consent. An unanswered verification goes to a human
     * rather than quietly letting the payment through.
     */
    @Scheduled(fixedDelayString = "${sentinel.outreach.sweep-ms:300000}")
    @Transactional
    public void expirePending() {
        List<OutreachAttempt> pending = outreachRepository.findAll().stream()
                .filter(a -> "PENDING".equals(a.getOutcome()))
                .filter(a -> a.getExpiresAt() != null && a.getExpiresAt().isBefore(Instant.now()))
                .toList();

        for (OutreachAttempt attempt : pending) {
            attempt.setOutcome("NO_RESPONSE");
            outreachRepository.save(attempt);
            if (attempt.getAlertId() != null) {
                alertRepository.findById(attempt.getAlertId()).ifPresent(alert -> {
                    if (alert.getStatus() == AlertStatus.OPEN) {
                        alert.setStatus(AlertStatus.INVESTIGATING);
                        alertRepository.save(alert);
                    }
                });
            }
        }
        if (!pending.isEmpty()) {
            log.info("{} outreach attempts expired without a response and were escalated", pending.size());
        }
    }
}
