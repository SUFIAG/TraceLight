package com.sentinel.fraud.rules.adapter.out;

import com.sentinel.fraud.rules.domain.model.FraudRule;
import com.sentinel.fraud.rules.domain.model.RuleResult;
import com.sentinel.transaction.adapter.out.persistence.TransactionRepository;
import com.sentinel.transaction.domain.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Detects a transaction from a country the customer has not transacted from
 * before. History is read as of the transaction's own timestamp and scoped to
 * the owning organization.
 */
@Component
@RequiredArgsConstructor
public class GeographicAnomalyRule implements FraudRule {

    private static final int HISTORY_DEPTH = 20;

    /**
     * Jurisdictions that carry elevated inherent risk for card-not-present fraud.
     * A first-time country is always an anomaly; a first-time country on this list
     * is a stronger one.
     */
    private static final Set<String> ELEVATED_RISK_COUNTRIES = Set.of(
            "NG", "RU", "VN", "UA", "ID", "PK", "BY", "KP", "IR", "SY"
    );

    private final TransactionRepository transactionRepository;

    @Override
    public String getName() {
        return "GEOGRAPHIC_ANOMALY";
    }

    @Override
    public String getDescription() {
        return "Detects transactions from unusual locations compared to user history";
    }

    @Override
    public RuleResult evaluate(Transaction transaction) {
        String country = transaction.getCountry();
        if (country == null || country.isBlank()) {
            return RuleResult.pass(getName());
        }

        List<Transaction> history = transactionRepository.findCountryHistoryBefore(
                transaction.getOrganizationId(),
                transaction.getUserId(),
                transaction.getTimestamp(),
                PageRequest.of(0, HISTORY_DEPTH));

        boolean elevated = ELEVATED_RISK_COUNTRIES.contains(country.toUpperCase());

        if (history.isEmpty()) {
            // No baseline yet. An unknown customer transacting from an elevated-risk
            // jurisdiction is still worth surfacing; anything else is simply new.
            return elevated
                    ? RuleResult.triggered(getName(), 55,
                        String.format("First recorded transaction for this customer originates from '%s', "
                                + "a jurisdiction with elevated card-not-present fraud rates", country))
                    : RuleResult.pass(getName());
        }

        Set<String> knownCountries = history.stream()
                .map(Transaction::getCountry)
                .collect(Collectors.toSet());

        if (knownCountries.contains(country)) {
            return RuleResult.pass(getName());
        }

        int contribution = elevated ? 85 : 70;
        return RuleResult.triggered(getName(), contribution,
                String.format("Transaction from '%s' is outside the customer's known footprint (%s)",
                        country, String.join(", ", knownCountries)));
    }
}
