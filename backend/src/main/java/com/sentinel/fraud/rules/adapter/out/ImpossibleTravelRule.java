package com.sentinel.fraud.rules.adapter.out;

import com.sentinel.fraud.rules.domain.model.FraudRule;
import com.sentinel.fraud.rules.domain.model.RuleResult;
import com.sentinel.transaction.adapter.out.persistence.TransactionRepository;
import com.sentinel.transaction.domain.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Flags a card present in two countries closer together in time than any
 * traveller could physically manage.
 *
 * <p>This is one of the few fraud signals with no legitimate explanation: a
 * geographic anomaly can be a holiday, but a card in London at 14:00 and in
 * Lagos at 14:40 is two cards. The rule compares the great-circle distance
 * between country centroids against the elapsed time and allows a generous
 * 900 km/h ceiling plus two hours of airport overhead, so genuine long-haul
 * travel never triggers it.
 */
@Component
@RequiredArgsConstructor
public class ImpossibleTravelRule implements FraudRule {

    /** Commercial aviation cruise speed, rounded up. */
    private static final double MAX_SPEED_KMH = 900.0;

    /** Check-in, transfer and immigration slack granted before the rule can fire. */
    private static final Duration TRAVEL_OVERHEAD = Duration.ofHours(2);

    /** Beyond this gap any journey is feasible, so the rule stops looking. */
    private static final Duration MAX_LOOKBACK = Duration.ofHours(24);

    /** Approximate country centroids (latitude, longitude). */
    private static final Map<String, double[]> CENTROIDS = Map.ofEntries(
            Map.entry("GB", new double[]{ 54.0,  -2.0}),
            Map.entry("US", new double[]{ 39.8, -98.6}),
            Map.entry("DE", new double[]{ 51.2,  10.4}),
            Map.entry("FR", new double[]{ 46.6,   2.2}),
            Map.entry("ES", new double[]{ 40.2,  -3.7}),
            Map.entry("NL", new double[]{ 52.1,   5.3}),
            Map.entry("AE", new double[]{ 24.0,  54.0}),
            Map.entry("SA", new double[]{ 24.0,  45.0}),
            Map.entry("NG", new double[]{  9.1,   8.7}),
            Map.entry("RU", new double[]{ 61.5,  99.0}),
            Map.entry("VN", new double[]{ 14.1, 108.3}),
            Map.entry("BR", new double[]{-14.2, -51.9}),
            Map.entry("UA", new double[]{ 48.4,  31.2}),
            Map.entry("ID", new double[]{ -2.5, 118.0}),
            Map.entry("IN", new double[]{ 20.6,  79.0}),
            Map.entry("CN", new double[]{ 35.9, 104.2}),
            Map.entry("JP", new double[]{ 36.2, 138.3}),
            Map.entry("AU", new double[]{-25.3, 133.8}),
            Map.entry("ZA", new double[]{-30.6,  22.9}),
            Map.entry("CA", new double[]{ 56.1,-106.3}),
            Map.entry("MX", new double[]{ 23.6,-102.6}),
            Map.entry("PK", new double[]{ 30.4,  69.3}),
            Map.entry("EG", new double[]{ 26.8,  30.8}),
            Map.entry("TR", new double[]{ 39.0,  35.2})
    );

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final TransactionRepository transactionRepository;

    @Override
    public String getName() {
        return "IMPOSSIBLE_TRAVEL";
    }

    @Override
    public String getDescription() {
        return "Detects the same card used in two countries faster than travel allows";
    }

    @Override
    public RuleResult evaluate(Transaction transaction) {
        String country = transaction.getCountry();
        Instant at = transaction.getTimestamp();
        if (country == null || country.isBlank() || at == null) {
            return RuleResult.pass(getName());
        }

        double[] here = CENTROIDS.get(country.toUpperCase());
        if (here == null) {
            return RuleResult.pass(getName());
        }

        List<Transaction> history = transactionRepository.findCountryHistoryBefore(
                transaction.getOrganizationId(), transaction.getUserId(), at, PageRequest.of(0, 5));

        for (Transaction previous : history) {
            Duration gap = Duration.between(previous.getTimestamp(), at);
            if (gap.isNegative() || gap.compareTo(MAX_LOOKBACK) > 0) {
                continue;
            }
            if (country.equalsIgnoreCase(previous.getCountry())) {
                continue;
            }

            double[] there = CENTROIDS.get(previous.getCountry().toUpperCase());
            if (there == null) {
                continue;
            }

            double distanceKm = haversineKm(here, there);
            double hoursAvailable = Math.max(0.0, gap.toMinutes() / 60.0 - hours(TRAVEL_OVERHEAD));
            double hoursRequired = distanceKm / MAX_SPEED_KMH;

            if (hoursAvailable < hoursRequired) {
                return RuleResult.triggered(getName(), 97, String.format(
                        "Card used in '%s' and '%s' %d minutes apart. The %,.0f km between them needs "
                                + "at least %.1f h of travel; only %.1f h were available.",
                        previous.getCountry(), country, gap.toMinutes(),
                        distanceKm, hoursRequired, hoursAvailable));
            }
        }

        return RuleResult.pass(getName());
    }

    private static double hours(Duration duration) {
        return duration.toMinutes() / 60.0;
    }

    private static double haversineKm(double[] a, double[] b) {
        double lat1 = Math.toRadians(a[0]);
        double lat2 = Math.toRadians(b[0]);
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(b[1] - a[1]);

        double h = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }
}
