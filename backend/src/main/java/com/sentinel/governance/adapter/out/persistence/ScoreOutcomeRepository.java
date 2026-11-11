package com.sentinel.governance.adapter.out.persistence;

import com.sentinel.fraud.risk.domain.model.RiskScore;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Score and outcome reads used by drift and fairness testing. Scoped to one
 * organization, because model behaviour is only meaningful against the
 * population it serves.
 */
public interface ScoreOutcomeRepository extends Repository<RiskScore, UUID> {

    @Query("SELECT r.score FROM RiskScore r, com.sentinel.transaction.domain.model.Transaction t " +
            "WHERE r.transactionId = t.id AND t.organizationId = :orgId " +
            "AND r.createdAt >= :from AND r.createdAt < :to")
    List<Integer> scoresBetween(@Param("orgId") UUID orgId,
                                @Param("from") Instant from,
                                @Param("to") Instant to);

    /** Segment, outcome, and score, for disparate-impact testing. */
    @Query("SELECT t.country, t.status, r.score FROM RiskScore r, " +
            "com.sentinel.transaction.domain.model.Transaction t " +
            "WHERE r.transactionId = t.id AND t.organizationId = :orgId " +
            "AND t.country IS NOT NULL AND r.createdAt > :since")
    List<Object[]> outcomesByCountry(@Param("orgId") UUID orgId, @Param("since") Instant since);

    @Query("SELECT t.currency, t.status, r.score FROM RiskScore r, " +
            "com.sentinel.transaction.domain.model.Transaction t " +
            "WHERE r.transactionId = t.id AND t.organizationId = :orgId AND r.createdAt > :since")
    List<Object[]> outcomesByCurrency(@Param("orgId") UUID orgId, @Param("since") Instant since);
}
