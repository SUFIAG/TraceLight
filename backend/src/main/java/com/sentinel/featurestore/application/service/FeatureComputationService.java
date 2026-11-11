package com.sentinel.featurestore.application.service;

import com.sentinel.featurestore.domain.model.FeatureDefinition;
import com.sentinel.graph.adapter.out.persistence.EntityLinkRepository;
import com.sentinel.mule.application.service.BeneficiaryRiskService;
import com.sentinel.transaction.adapter.out.persistence.TransactionRepository;
import com.sentinel.transaction.domain.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Computes the feature vector for one transaction.
 *
 * <p>Every window is anchored on the transaction's own timestamp, never on
 * {@code now()}. This is the difference between a feature store that can be
 * trusted for training and one that quietly leaks the future: a payment loaded
 * last week must see exactly the history that existed when it happened, or
 * every backtest overstates the model.
 */
@Service
@RequiredArgsConstructor
public class FeatureComputationService {

    private final TransactionRepository transactionRepository;
    private final BeneficiaryRiskService beneficiaryRiskService;
    private final EntityLinkRepository entityLinkRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> compute(UUID orgId, Transaction transaction) {
        Map<String, Object> features = new HashMap<>();
        Instant asOf = transaction.getTimestamp();
        String userId = transaction.getUserId();

        features.put(FeatureDefinition.AMOUNT.getKey(), transaction.getAmount());
        features.put(FeatureDefinition.HOUR_OF_DAY.getKey(), asOf.atZone(ZoneOffset.UTC).getHour());
        features.put(FeatureDefinition.COUNTRY.getKey(), transaction.getCountry());
        features.put(FeatureDefinition.CURRENCY.getKey(), transaction.getCurrency());
        features.put(FeatureDefinition.MERCHANT_ID.getKey(), transaction.getMerchantId());

        features.put(FeatureDefinition.TX_COUNT_1H.getKey(),
                transactionRepository.countInWindowByUserId(orgId, userId, asOf.minus(1, ChronoUnit.HOURS), asOf));
        features.put(FeatureDefinition.TX_COUNT_24H.getKey(),
                transactionRepository.countInWindowByUserId(orgId, userId, asOf.minus(24, ChronoUnit.HOURS), asOf));
        features.put(FeatureDefinition.TX_COUNT_7D.getKey(),
                transactionRepository.countInWindowByUserId(orgId, userId, asOf.minus(7, ChronoUnit.DAYS), asOf));

        features.put(FeatureDefinition.AMOUNT_SUM_24H.getKey(),
                transactionRepository.sumAmountInWindowByUserId(orgId, userId, asOf.minus(24, ChronoUnit.HOURS), asOf));
        features.put(FeatureDefinition.AMOUNT_SUM_7D.getKey(),
                transactionRepository.sumAmountInWindowByUserId(orgId, userId, asOf.minus(7, ChronoUnit.DAYS), asOf));

        features.put(FeatureDefinition.DISTINCT_MERCHANTS_24H.getKey(),
                transactionRepository.countDistinctMerchantsInWindow(orgId, userId, asOf.minus(24, ChronoUnit.HOURS), asOf));
        features.put(FeatureDefinition.DISTINCT_COUNTRIES_24H.getKey(),
                transactionRepository.countDistinctCountriesInWindow(orgId, userId, asOf.minus(24, ChronoUnit.HOURS), asOf));

        BigDecimal average = transactionRepository.findAverageAmountByUserId(orgId, userId);
        long lifetime = transactionRepository.countByOrganizationIdAndUserId(orgId, userId);
        features.put(FeatureDefinition.AVG_AMOUNT_LIFETIME.getKey(), average);
        features.put(FeatureDefinition.TX_COUNT_LIFETIME.getKey(), lifetime);
        features.put(FeatureDefinition.AMOUNT_RATIO_TO_AVERAGE.getKey(),
                average == null || average.signum() == 0
                        ? BigDecimal.ZERO
                        : transaction.getAmount().divide(average, 3, RoundingMode.HALF_UP));

        int beneficiaryRisk = beneficiaryRiskService.riskOf(orgId, transaction.getMerchantId());
        features.put(FeatureDefinition.BENEFICIARY_RISK.getKey(), beneficiaryRisk);
        features.put(FeatureDefinition.BENEFICIARY_CONFIRMED_MULE.getKey(),
                beneficiaryRiskService.isConfirmedMule(orgId, transaction.getMerchantId()));

        features.put(FeatureDefinition.GRAPH_DEGREE.getKey(),
                entityLinkRepository.findNeighbours(orgId, userId).size());

        boolean newPayee = transactionRepository.findByOrganizationIdAndUserId(orgId, userId).stream()
                .filter(t -> t.getTimestamp().isBefore(asOf))
                .noneMatch(t -> t.getMerchantId().equals(transaction.getMerchantId()));
        features.put(FeatureDefinition.IS_NEW_PAYEE.getKey(), newPayee);

        return features;
    }
}
