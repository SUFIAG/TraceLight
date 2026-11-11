package com.sentinel.analytics.application.service;

import com.sentinel.transaction.adapter.out.persistence.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Short-horizon fraud forecasting — the "fraud weather forecast".
 *
 * <p>Holt linear exponential smoothing over the daily flagged series: a level
 * term and a trend term, which is enough for a two-week horizon and, unlike a
 * fitted model, needs no training data and degrades gracefully on sparse
 * history.
 *
 * <p>Calendar pressure is layered on top, because attack volume is seasonal in
 * a way the series alone will not anticipate: weekends, month end, and the
 * shopping peaks when card testing reliably rises.
 */
@Service
@RequiredArgsConstructor
public class LossForecastService {

    /** Level smoothing. Higher reacts faster and is noisier. */
    private static final double ALPHA = 0.45;
    /** Trend smoothing. */
    private static final double BETA = 0.25;
    private static final int MIN_HISTORY_DAYS = 7;

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> forecast(UUID orgId, int historyDays, int horizonDays) {
        Instant since = Instant.now().minus(historyDays, ChronoUnit.DAYS);
        List<Object[]> rows = transactionRepository.findDailyFraudTrend(orgId, since);

        List<Map<String, Object>> history = new ArrayList<>();
        List<Double> series = new ArrayList<>();
        for (Object[] row : rows) {
            double flagged = ((Number) row[2]).doubleValue();
            series.add(flagged);
            history.add(Map.of(
                    "date", String.valueOf(row[0]),
                    "total", ((Number) row[1]).longValue(),
                    "flagged", (long) flagged));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("history", history);

        if (series.size() < MIN_HISTORY_DAYS) {
            result.put("forecast", List.of());
            result.put("confidence", "INSUFFICIENT_HISTORY");
            result.put("summary", "Only " + series.size() + " days of history. At least "
                    + MIN_HISTORY_DAYS + " are needed to project a trend.");
            return result;
        }

        double level = series.get(0);
        double trend = series.get(1) - series.get(0);
        for (int i = 1; i < series.size(); i++) {
            double previousLevel = level;
            level = ALPHA * series.get(i) + (1 - ALPHA) * (level + trend);
            trend = BETA * (level - previousLevel) + (1 - BETA) * trend;
        }

        double residual = residualStandardDeviation(series);
        List<Map<String, Object>> forecast = new ArrayList<>();
        LocalDate day = LocalDate.now(ZoneOffset.UTC);

        for (int h = 1; h <= horizonDays; h++) {
            LocalDate target = day.plusDays(h);
            double base = Math.max(0, level + h * trend);
            double multiplier = calendarPressure(target);
            double projected = base * multiplier;
            // The interval widens with the horizon: uncertainty compounds.
            double interval = residual * Math.sqrt(h);

            forecast.add(Map.of(
                    "date", target.toString(),
                    "expectedFlagged", Math.round(projected),
                    "lowerBound", Math.max(0, Math.round(projected - interval)),
                    "upperBound", Math.round(projected + interval),
                    "calendarMultiplier", Math.round(multiplier * 100) / 100.0,
                    "drivers", driversFor(target)));
        }

        double weekAhead = forecast.stream()
                .limit(7)
                .mapToDouble(f -> ((Number) f.get("expectedFlagged")).doubleValue())
                .sum();
        double weekBehind = series.stream().skip(Math.max(0, series.size() - 7)).mapToDouble(Double::doubleValue).sum();
        double change = weekBehind == 0 ? 0 : (weekAhead - weekBehind) / weekBehind * 100;

        result.put("forecast", forecast);
        result.put("confidence", series.size() >= 28 ? "GOOD" : "LIMITED");
        result.put("trendPerDay", Math.round(trend * 100) / 100.0);
        result.put("summary", String.format(
                "Projecting %.0f flagged transactions over the next 7 days against %.0f in the last 7, "
                        + "a change of %+.0f%%. Trend is %+.1f per day.",
                weekAhead, weekBehind, change, trend));
        return result;
    }

    /** Known seasonal pressure on fraud volume. */
    private double calendarPressure(LocalDate date) {
        double multiplier = 1.0;
        switch (date.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> multiplier *= 1.15;
            case FRIDAY -> multiplier *= 1.08;
            default -> multiplier *= 1.0;
        }
        // Month end concentrates payroll, rent, and invoice-redirection attempts.
        if (date.getDayOfMonth() >= date.lengthOfMonth() - 2 || date.getDayOfMonth() <= 2) {
            multiplier *= 1.10;
        }
        // Late-November peak shopping is the reliable card-testing season.
        if (date.getMonthValue() == 11 && date.getDayOfMonth() >= 20) {
            multiplier *= 1.35;
        }
        if (date.getMonthValue() == 12 && date.getDayOfMonth() <= 24) {
            multiplier *= 1.20;
        }
        return multiplier;
    }

    private List<String> driversFor(LocalDate date) {
        List<String> drivers = new ArrayList<>();
        switch (date.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> drivers.add("Weekend: reduced manual review cover");
            case FRIDAY -> drivers.add("Friday: elevated payment volume");
            default -> { }
        }
        if (date.getDayOfMonth() >= date.lengthOfMonth() - 2 || date.getDayOfMonth() <= 2) {
            drivers.add("Month end: payroll and invoice cycle");
        }
        if (date.getMonthValue() == 11 && date.getDayOfMonth() >= 20) {
            drivers.add("Peak shopping season: card testing rises");
        }
        if (date.getMonthValue() == 12 && date.getDayOfMonth() <= 24) {
            drivers.add("Holiday period: purchase scams rise");
        }
        return drivers;
    }

    private double residualStandardDeviation(List<Double> series) {
        double mean = series.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = series.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElse(0);
        return Math.sqrt(variance);
    }
}
