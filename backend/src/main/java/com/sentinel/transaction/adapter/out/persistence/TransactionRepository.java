package com.sentinel.transaction.adapter.out.persistence;

import com.sentinel.transaction.domain.model.Transaction;
import com.sentinel.transaction.domain.model.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<Transaction> findByOrganizationIdAndStatus(UUID organizationId, TransactionStatus status, Pageable pageable);

    // Behavioural queries below are keyed on userId, which is a customer identifier
    // supplied by the tenant and therefore NOT globally unique. Every one of them is
    // scoped by organizationId so one tenant's history can never influence another
    // tenant's risk score.

    @Query("SELECT t FROM Transaction t WHERE t.organizationId = :orgId AND t.userId = :userId " +
            "AND t.timestamp > :since ORDER BY t.timestamp DESC")
    List<Transaction> findRecentByUserId(@Param("orgId") UUID organizationId,
                                         @Param("userId") String userId,
                                         @Param("since") Instant since);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.organizationId = :orgId AND t.userId = :userId " +
            "AND t.timestamp > :since AND t.timestamp <= :until")
    long countInWindowByUserId(@Param("orgId") UUID organizationId,
                               @Param("userId") String userId,
                               @Param("since") Instant since,
                               @Param("until") Instant until);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.organizationId = :orgId AND t.createdAt > :since")
    long countByOrganizationIdSince(@Param("orgId") UUID orgId, @Param("since") Instant since);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.organizationId = :orgId AND t.status = :status AND t.createdAt > :since")
    long countByOrganizationIdAndStatusSince(@Param("orgId") UUID orgId, @Param("status") TransactionStatus status, @Param("since") Instant since);

    @Query("SELECT COALESCE(AVG(t.amount), 0) FROM Transaction t " +
            "WHERE t.organizationId = :orgId AND t.userId = :userId")
    BigDecimal findAverageAmountByUserId(@Param("orgId") UUID organizationId,
                                         @Param("userId") String userId);

    /**
     * The customer's country history as of {@code before}. Bounding on the
     * transaction's own time rather than "now" means a backfilled or CSV-loaded
     * transaction is judged against the history that preceded it, not against
     * rows that were loaded in the same batch.
     */
    @Query("SELECT t FROM Transaction t WHERE t.organizationId = :orgId AND t.userId = :userId " +
            "AND t.country IS NOT NULL AND t.timestamp < :before ORDER BY t.timestamp DESC")
    List<Transaction> findCountryHistoryBefore(@Param("orgId") UUID organizationId,
                                               @Param("userId") String userId,
                                               @Param("before") Instant before,
                                               Pageable pageable);

    List<Transaction> findByOrganizationIdAndUserId(UUID organizationId, String userId);

    long countByOrganizationIdAndUserId(UUID organizationId, String userId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.userId = :userId AND t.timestamp > :since AND t.timestamp <= :until")
    BigDecimal sumAmountInWindowByUserId(@Param("orgId") UUID organizationId,
                                         @Param("userId") String userId,
                                         @Param("since") Instant since,
                                         @Param("until") Instant until);

    @Query("SELECT COUNT(DISTINCT t.merchantId) FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.userId = :userId AND t.timestamp > :since AND t.timestamp <= :until")
    long countDistinctMerchantsInWindow(@Param("orgId") UUID organizationId,
                                        @Param("userId") String userId,
                                        @Param("since") Instant since,
                                        @Param("until") Instant until);

    @Query("SELECT COUNT(DISTINCT t.country) FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.userId = :userId AND t.timestamp > :since AND t.timestamp <= :until")
    long countDistinctCountriesInWindow(@Param("orgId") UUID organizationId,
                                        @Param("userId") String userId,
                                        @Param("since") Instant since,
                                        @Param("until") Instant until);

    // ----- Analytics / Reporting aggregations (Phase 4) -----

    @Query("SELECT t.merchantId, COUNT(t) FROM Transaction t " +
            "WHERE t.organizationId = :orgId AND t.status = :status " +
            "GROUP BY t.merchantId ORDER BY COUNT(t) DESC")
    List<Object[]> findTopMerchantsByStatus(@Param("orgId") UUID orgId,
                                            @Param("status") TransactionStatus status,
                                            Pageable pageable);

    @Query("SELECT t.country, COUNT(t), " +
            "SUM(CASE WHEN t.status = :flagged THEN 1 ELSE 0 END) " +
            "FROM Transaction t WHERE t.organizationId = :orgId AND t.country IS NOT NULL " +
            "GROUP BY t.country ORDER BY COUNT(t) DESC")
    List<Object[]> findGeographicBreakdown(@Param("orgId") UUID orgId,
                                           @Param("flagged") TransactionStatus flagged);

    // Bucketed by the transaction's own timestamp rather than row insert time, so the
    // trend reflects when activity happened, not when the data was loaded.
    // "timestamp" is quoted because it is a reserved word in PostgreSQL.
    @Query(value = "SELECT CAST(t.\"timestamp\" AS DATE) AS day, COUNT(*) AS total, " +
            "COUNT(*) FILTER (WHERE t.status = 'FLAGGED') AS flagged " +
            "FROM transactions t WHERE t.organization_id = :orgId AND t.\"timestamp\" > :since " +
            "GROUP BY CAST(t.\"timestamp\" AS DATE) ORDER BY day", nativeQuery = true)
    List<Object[]> findDailyFraudTrend(@Param("orgId") UUID orgId, @Param("since") Instant since);
}
