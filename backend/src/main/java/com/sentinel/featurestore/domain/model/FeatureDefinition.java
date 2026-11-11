package com.sentinel.featurestore.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * The feature catalogue.
 *
 * <p>Held as an enum rather than a table on purpose: every feature here is
 * computed by code, and a database row describing a feature no code computes
 * is how feature stores rot. Adding a feature means adding a case to the
 * computation service, and the catalogue cannot drift away from it.
 *
 * <p>Each feature names its window explicitly. Point-in-time correctness is
 * the whole value of a feature store — a model trained on "transactions in the
 * last 24 hours" computed with today's data has seen the future, and will
 * perform far better in backtest than it ever will in production.
 */
@Getter
@RequiredArgsConstructor
public enum FeatureDefinition {

    AMOUNT("amount", "Transaction amount", "NUMERIC", "point", "The value of this payment"),
    HOUR_OF_DAY("hourOfDay", "Hour of day (UTC)", "NUMERIC", "point", "0-23, used for off-hours detection"),
    COUNTRY("country", "Country", "CATEGORICAL", "point", "ISO country of the payment"),
    CURRENCY("currency", "Currency", "CATEGORICAL", "point", "ISO currency of the payment"),
    MERCHANT_ID("merchantId", "Merchant or beneficiary", "CATEGORICAL", "point", "The receiving party"),

    TX_COUNT_1H("txCount1h", "Payments in the last hour", "NUMERIC", "1h", "Velocity over a short window"),
    TX_COUNT_24H("txCount24h", "Payments in the last 24 hours", "NUMERIC", "24h", "Daily velocity"),
    TX_COUNT_7D("txCount7d", "Payments in the last 7 days", "NUMERIC", "7d", "Weekly velocity"),
    AMOUNT_SUM_24H("amountSum24h", "Value sent in the last 24 hours", "NUMERIC", "24h", "Daily exposure"),
    AMOUNT_SUM_7D("amountSum7d", "Value sent in the last 7 days", "NUMERIC", "7d", "Weekly exposure"),

    DISTINCT_MERCHANTS_24H("distinctMerchants24h", "Distinct payees in 24 hours", "NUMERIC", "24h",
            "Spraying across many payees is a mule and card-testing signal"),
    DISTINCT_COUNTRIES_24H("distinctCountries24h", "Distinct countries in 24 hours", "NUMERIC", "24h",
            "Geographic dispersion inside one day"),

    AVG_AMOUNT_LIFETIME("avgAmountLifetime", "Lifetime average payment", "NUMERIC", "lifetime",
            "The customer's own baseline, not the population's"),
    AMOUNT_RATIO_TO_AVERAGE("amountRatioToAverage", "Amount relative to the customer average", "NUMERIC", "lifetime",
            "How far this payment deviates from the customer's normal"),
    TX_COUNT_LIFETIME("txCountLifetime", "Lifetime payment count", "NUMERIC", "lifetime",
            "How much history exists to judge against"),

    BENEFICIARY_RISK("beneficiaryRisk", "Beneficiary mule score", "NUMERIC", "30d",
            "Risk held against the receiving account"),
    BENEFICIARY_CONFIRMED_MULE("beneficiaryConfirmedMule", "Beneficiary is a confirmed mule", "BOOLEAN", "lifetime",
            "Set when the destination has been confirmed by an analyst or the consortium"),

    GRAPH_DEGREE("graphDegree", "Links in the fraud graph", "NUMERIC", "90d",
            "How many other accounts this customer is connected to"),
    IS_NEW_PAYEE("isNewPayee", "First payment to this payee", "BOOLEAN", "lifetime",
            "No prior payment from this customer to this beneficiary");

    private final String key;
    private final String label;
    private final String type;
    private final String window;
    private final String description;

    public static Optional<FeatureDefinition> byKey(String key) {
        return Arrays.stream(values()).filter(f -> f.key.equals(key)).findFirst();
    }
}
