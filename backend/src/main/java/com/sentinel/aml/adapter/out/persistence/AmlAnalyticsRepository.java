package com.sentinel.aml.adapter.out.persistence;

import com.sentinel.transaction.domain.model.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Two-sided reads over the transaction ledger.
 *
 * <p>AML questions are about flow, not about single payments: what came in,
 * what went out, and how quickly one followed the other. The payer side is
 * {@code userId} and the payee side is {@code merchantId}, so every query here
 * comes in a pair.
 */
public interface AmlAnalyticsRepository extends Repository<Transaction, UUID> {

    @Query("SELECT DISTINCT t.userId FROM Transaction t WHERE t.organizationId = :orgId AND t.timestamp > :since")
    List<String> activeSubjects(@Param("orgId") UUID orgId, @Param("since") Instant since, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.organizationId = :orgId AND t.userId = :subjectId " +
            "AND t.timestamp > :since ORDER BY t.timestamp ASC")
    List<Transaction> outbound(@Param("orgId") UUID orgId,
                               @Param("subjectId") String subjectId,
                               @Param("since") Instant since);

    @Query("SELECT t FROM Transaction t WHERE t.organizationId = :orgId AND t.merchantId = :subjectId " +
            "AND t.timestamp > :since ORDER BY t.timestamp ASC")
    List<Transaction> inbound(@Param("orgId") UUID orgId,
                              @Param("subjectId") String subjectId,
                              @Param("since") Instant since);

    /** Most recent activity strictly before a point in time — used to measure dormancy. */
    @Query("SELECT t FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND (t.userId = :subjectId OR t.merchantId = :subjectId) AND t.timestamp < :before " +
            "ORDER BY t.timestamp DESC")
    List<Transaction> activityBefore(@Param("orgId") UUID orgId,
                                     @Param("subjectId") String subjectId,
                                     @Param("before") Instant before,
                                     Pageable pageable);

    /** Did value ever flow back the other way? The round-tripping test. */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.userId = :from AND t.merchantId = :to AND t.timestamp > :since")
    long countFlow(@Param("orgId") UUID orgId,
                   @Param("from") String from,
                   @Param("to") String to,
                   @Param("since") Instant since);
}
