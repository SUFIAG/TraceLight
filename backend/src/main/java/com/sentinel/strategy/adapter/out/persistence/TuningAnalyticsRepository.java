package com.sentinel.strategy.adapter.out.persistence;

import com.sentinel.fraud.risk.domain.model.RiskScore;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Score-and-outcome pairs for threshold tuning. One row per scored
 * transaction: the score it received, what happened to it, and what it was
 * worth.
 */
public interface TuningAnalyticsRepository extends Repository<RiskScore, UUID> {

    @Query("SELECT r.score, t.status, t.amount FROM RiskScore r, " +
            "com.sentinel.transaction.domain.model.Transaction t " +
            "WHERE r.transactionId = t.id AND t.organizationId = :orgId AND r.createdAt > :since")
    List<Object[]> scoredOutcomes(@Param("orgId") UUID orgId, @Param("since") Instant since);
}
