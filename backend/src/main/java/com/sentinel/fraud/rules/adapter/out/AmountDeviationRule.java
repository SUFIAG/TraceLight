package com.sentinel.fraud.rules.adapter.out;

import com.sentinel.fraud.rules.domain.model.FraudRule;
import com.sentinel.fraud.rules.domain.model.RuleResult;
import com.sentinel.transaction.adapter.out.persistence.TransactionRepository;
import com.sentinel.transaction.domain.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Compares a transaction against the customer's own spending baseline rather
 * than against a fixed threshold.
 *
 * <p>A flat "over $5,000 is suspicious" rule is wrong in both directions: it
 * never fires for the customer whose entire history is £20 coffees and who
 * suddenly spends £900, and it fires constantly for the private-banking client
 * for whom $5,000 is a Tuesday. This rule asks the only question that
 * generalises across both: how far is this from what <em>this</em> customer
 * normally does?
 */
@Component
@RequiredArgsConstructor
public class AmountDeviationRule implements FraudRule {

    /** Below this many prior transactions the baseline is not trustworthy. */
    private static final int MIN_HISTORY = 4;

    /** Ignore deviations on trivially small baselines to avoid noise. */
    private static final BigDecimal MIN_BASELINE = new BigDecimal("5.00");

    private final TransactionRepository transactionRepository;

    @Override
    public String getName() {
        return "AMOUNT_DEVIATION";
    }

    @Override
    public String getDescription() {
        return "Flags amounts far outside the customer's own historical spending pattern";
    }

    @Override
    public RuleResult evaluate(Transaction transaction) {
        long historyCount = transactionRepository.countByOrganizationIdAndUserId(
                transaction.getOrganizationId(), transaction.getUserId());

        // The transaction being scored is already persisted, so exclude it.
        if (historyCount - 1 < MIN_HISTORY) {
            return RuleResult.pass(getName());
        }

        BigDecimal baseline = transactionRepository.findAverageAmountByUserId(
                transaction.getOrganizationId(), transaction.getUserId());

        if (baseline == null || baseline.compareTo(MIN_BASELINE) < 0) {
            return RuleResult.pass(getName());
        }

        BigDecimal ratio = transaction.getAmount()
                .divide(baseline, 2, RoundingMode.HALF_UP);
        double multiple = ratio.doubleValue();

        if (multiple >= 20.0) {
            return RuleResult.triggered(getName(), 90, String.format(
                    "Amount is %.0f× this customer's average of %s — far outside their established pattern",
                    multiple, baseline.setScale(2, RoundingMode.HALF_UP)));
        }
        if (multiple >= 10.0) {
            return RuleResult.triggered(getName(), 72, String.format(
                    "Amount is %.0f× this customer's average of %s",
                    multiple, baseline.setScale(2, RoundingMode.HALF_UP)));
        }
        if (multiple >= 5.0) {
            return RuleResult.triggered(getName(), 50, String.format(
                    "Amount is %.1f× this customer's average of %s",
                    multiple, baseline.setScale(2, RoundingMode.HALF_UP)));
        }
        return RuleResult.pass(getName());
    }
}
