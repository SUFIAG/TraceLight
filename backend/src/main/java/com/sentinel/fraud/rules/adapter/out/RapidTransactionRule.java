package com.sentinel.fraud.rules.adapter.out;

import com.sentinel.fraud.rules.domain.model.FraudRule;
import com.sentinel.fraud.rules.domain.model.RuleResult;
import com.sentinel.transaction.adapter.out.persistence.TransactionRepository;
import com.sentinel.transaction.domain.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Detects a burst of transactions from one customer. The window ends at the
 * transaction's own timestamp rather than at wall-clock time, so the rule works
 * identically for live traffic, CSV backfill and replay.
 */
@Component
@RequiredArgsConstructor
public class RapidTransactionRule implements FraudRule {

    private static final int BURST_THRESHOLD = 5;
    private static final int ELEVATED_THRESHOLD = 3;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final TransactionRepository transactionRepository;

    @Override
    public String getName() {
        return "RAPID_TRANSACTION";
    }

    @Override
    public String getDescription() {
        return "Detects rapid succession of transactions from same user";
    }

    @Override
    public RuleResult evaluate(Transaction transaction) {
        Instant at = transaction.getTimestamp() != null ? transaction.getTimestamp() : Instant.now();

        long count = transactionRepository.countInWindowByUserId(
                transaction.getOrganizationId(), transaction.getUserId(), at.minus(WINDOW), at);

        if (count >= BURST_THRESHOLD) {
            return RuleResult.triggered(getName(), 88,
                    String.format("%d transactions in %d minutes (threshold: %d) — consistent with card testing",
                            count, WINDOW.toMinutes(), BURST_THRESHOLD));
        }
        if (count >= ELEVATED_THRESHOLD) {
            return RuleResult.triggered(getName(), 60,
                    String.format("%d transactions in %d minutes exceeds this customer's normal pace",
                            count, WINDOW.toMinutes()));
        }
        return RuleResult.pass(getName());
    }
}
