package com.sentinel.mule.adapter.out.persistence;

import com.sentinel.transaction.domain.model.Transaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only projections over {@code transactions} from the receiving side.
 *
 * <p>A separate interface rather than more methods on {@code TransactionRepository}:
 * mule analytics are a different reading of the same rows, and keeping the
 * queries beside the module that owns them stops the transaction repository
 * from turning into the place where every team adds one more query.
 */
public interface BeneficiaryAnalyticsRepository extends Repository<Transaction, UUID> {

    @Query("SELECT COUNT(DISTINCT t.userId) FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.merchantId = :beneficiaryId AND t.timestamp > :since")
    long countDistinctPayers(@Param("orgId") UUID orgId,
                             @Param("beneficiaryId") String beneficiaryId,
                             @Param("since") Instant since);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.merchantId = :beneficiaryId AND t.timestamp > :since")
    long countCredits(@Param("orgId") UUID orgId,
                      @Param("beneficiaryId") String beneficiaryId,
                      @Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.merchantId = :beneficiaryId AND t.timestamp > :since")
    BigDecimal sumCredited(@Param("orgId") UUID orgId,
                           @Param("beneficiaryId") String beneficiaryId,
                           @Param("since") Instant since);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.merchantId = :beneficiaryId AND t.timestamp > :since AND t.amount < :threshold")
    long countSmallCredits(@Param("orgId") UUID orgId,
                           @Param("beneficiaryId") String beneficiaryId,
                           @Param("since") Instant since,
                           @Param("threshold") BigDecimal threshold);

    @Query("SELECT COUNT(DISTINCT t.country) FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.merchantId = :beneficiaryId AND t.country IS NOT NULL AND t.timestamp > :since")
    long countDistinctPayerCountries(@Param("orgId") UUID orgId,
                                     @Param("beneficiaryId") String beneficiaryId,
                                     @Param("since") Instant since);

    @Query("SELECT MIN(t.timestamp), MAX(t.timestamp) FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.merchantId = :beneficiaryId")
    List<Object[]> activityWindow(@Param("orgId") UUID orgId,
                                  @Param("beneficiaryId") String beneficiaryId);

    /** Beneficiaries ranked by distinct payers — the fan-in leaderboard a mule hunter opens first. */
    @Query("SELECT t.merchantId, COUNT(DISTINCT t.userId), COUNT(t), COALESCE(SUM(t.amount), 0) " +
            "FROM Transaction t WHERE t.organizationId = :orgId AND t.timestamp > :since " +
            "GROUP BY t.merchantId HAVING COUNT(DISTINCT t.userId) >= :minPayers " +
            "ORDER BY COUNT(DISTINCT t.userId) DESC")
    List<Object[]> topFanIn(@Param("orgId") UUID orgId,
                            @Param("since") Instant since,
                            @Param("minPayers") long minPayers);

    @Query("SELECT DISTINCT t.userId FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.merchantId = :beneficiaryId AND t.timestamp > :since")
    List<String> findPayers(@Param("orgId") UUID orgId,
                            @Param("beneficiaryId") String beneficiaryId,
                            @Param("since") Instant since);
}
