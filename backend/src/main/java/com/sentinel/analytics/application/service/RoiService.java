package com.sentinel.analytics.application.service;

import com.sentinel.alert.adapter.out.persistence.AlertRepository;
import com.sentinel.alert.domain.model.AlertStatus;
import com.sentinel.autonomy.adapter.out.persistence.AgentRunRepository;
import com.sentinel.disputes.adapter.out.persistence.DisputeRepository;
import com.sentinel.friction.adapter.out.persistence.FrictionDecisionRepository;
import com.sentinel.friction.domain.model.FrictionMethod;
import com.sentinel.scam.adapter.out.persistence.ScamSignalRepository;
import com.sentinel.transaction.adapter.out.persistence.TransactionRepository;
import com.sentinel.transaction.domain.model.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The renewal conversation, as a number.
 *
 * <p>Fraud platforms are bought on fear and renewed on evidence. The evidence
 * a buyer needs has four parts, and most vendors report only the first:
 * fraud stopped, analyst hours returned, false positives avoided, and
 * customers not needlessly interrupted.
 *
 * <p>Every figure is derived from recorded activity, and the assumptions that
 * convert counts into money are stated in the response rather than buried,
 * because a number whose assumptions are hidden is a number a CFO will not
 * accept twice.
 */
@Service
@RequiredArgsConstructor
public class RoiService {

    /** Fully loaded cost of an analyst hour. */
    private static final double ANALYST_HOURLY_COST = 45.0;
    /** Minutes a human spends on one alert without assistance. */
    private static final double MINUTES_PER_ALERT = 12.0;
    /** Share of a wrongly stopped payment's value treated as customer-experience cost. */
    private static final double FRICTION_COST_RATE = 0.05;

    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;
    private final AgentRunRepository agentRunRepository;
    private final ScamSignalRepository scamSignalRepository;
    private final DisputeRepository disputeRepository;
    private final FrictionDecisionRepository frictionDecisionRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> summary(UUID orgId, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        long transactions = transactionRepository.countByOrganizationIdSince(orgId, since);
        long flagged = transactionRepository.countByOrganizationIdAndStatusSince(
                orgId, TransactionStatus.FLAGGED, since);
        long rejected = transactionRepository.countByOrganizationIdAndStatusSince(
                orgId, TransactionStatus.REJECTED, since);

        long alertsRaised = alertRepository.countByOrganizationIdAndCreatedAtAfter(orgId, since);
        long falsePositives = alertRepository.countByOrganizationIdAndStatus(orgId, AlertStatus.FALSE_POSITIVE);

        long agentRuns = agentRunRepository.countSince(orgId, since);
        long agentActions = agentRunRepository.countAutomatedSince(orgId, since);

        long scamInterventions = scamSignalRepository.countInterventionsSince(orgId, since);
        BigDecimal disputeRecovered = disputeRepository.totalRecovered(orgId);

        long frictionDecisions = frictionDecisionRepository.countByOrganizationIdAndCreatedAtAfter(orgId, since);
        long frictionless = frictionDecisionRepository.countByOrganizationIdAndMethodAndCreatedAtAfter(
                orgId, FrictionMethod.NONE, since);

        // Hours returned: work the agents completed that a human would otherwise have done.
        double hoursSaved = agentActions * MINUTES_PER_ALERT / 60.0;
        double analystCostSaved = hoursSaved * ANALYST_HOURLY_COST;

        Map<String, Object> detection = new LinkedHashMap<>();
        detection.put("transactionsScored", transactions);
        detection.put("transactionsFlagged", flagged);
        detection.put("transactionsRejected", rejected);
        detection.put("alertsRaised", alertsRaised);
        detection.put("scamInterventions", scamInterventions);

        Map<String, Object> automation = new LinkedHashMap<>();
        automation.put("agentRuns", agentRuns);
        automation.put("actionsTakenAutonomously", agentActions);
        automation.put("automationRate", agentRuns == 0 ? 0
                : Math.round((double) agentActions / agentRuns * 1000) / 10.0);
        automation.put("analystHoursReturned", Math.round(hoursSaved * 10) / 10.0);
        automation.put("analystCostAvoided", Math.round(analystCostSaved));

        Map<String, Object> experience = new LinkedHashMap<>();
        experience.put("frictionDecisions", frictionDecisions);
        experience.put("customersPassedWithoutFriction", frictionless);
        experience.put("frictionlessRate", frictionDecisions == 0 ? null
                : Math.round((double) frictionless / frictionDecisions * 1000) / 10.0);
        experience.put("falsePositivesClosed", falsePositives);

        Map<String, Object> recovery = new LinkedHashMap<>();
        recovery.put("disputeValueRecovered", disputeRecovered);

        Map<String, Object> assumptions = new LinkedHashMap<>();
        assumptions.put("analystHourlyCost", ANALYST_HOURLY_COST);
        assumptions.put("minutesPerManualAlert", MINUTES_PER_ALERT);
        assumptions.put("frictionCostRateOfStoppedValue", FRICTION_COST_RATE);
        assumptions.put("note", "Counts are measured from recorded activity. "
                + "Monetary figures apply the stated assumptions and should be agreed with the client.");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("windowDays", days);
        summary.put("detection", detection);
        summary.put("automation", automation);
        summary.put("customerExperience", experience);
        summary.put("recovery", recovery);
        summary.put("assumptions", assumptions);
        return summary;
    }
}
