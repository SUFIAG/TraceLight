package com.sentinel.graph.adapter.out.persistence;

import com.sentinel.transaction.domain.model.Transaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Shared-attribute projections used to build the fraud graph. Each query
 * returns only attributes that more than one customer touched, because an
 * attribute with a single owner cannot produce an edge.
 */
public interface GraphAnalyticsRepository extends Repository<Transaction, UUID> {

    @Query("SELECT t.deviceId FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.deviceId IS NOT NULL AND t.timestamp > :since " +
            "GROUP BY t.deviceId HAVING COUNT(DISTINCT t.userId) > 1 AND COUNT(DISTINCT t.userId) <= :maxFanOut")
    List<String> sharedDevices(@Param("orgId") UUID orgId,
                               @Param("since") Instant since,
                               @Param("maxFanOut") long maxFanOut);

    @Query("SELECT t.ipAddress FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.ipAddress IS NOT NULL AND t.timestamp > :since " +
            "GROUP BY t.ipAddress HAVING COUNT(DISTINCT t.userId) > 1 AND COUNT(DISTINCT t.userId) <= :maxFanOut")
    List<String> sharedIps(@Param("orgId") UUID orgId,
                           @Param("since") Instant since,
                           @Param("maxFanOut") long maxFanOut);

    @Query("SELECT t.merchantId FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.timestamp > :since GROUP BY t.merchantId " +
            "HAVING COUNT(DISTINCT t.userId) > 1 AND COUNT(DISTINCT t.userId) <= :maxFanOut")
    List<String> sharedBeneficiaries(@Param("orgId") UUID orgId,
                                     @Param("since") Instant since,
                                     @Param("maxFanOut") long maxFanOut);

    @Query("SELECT DISTINCT t.userId FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.deviceId = :value AND t.timestamp > :since")
    List<String> customersByDevice(@Param("orgId") UUID orgId,
                                   @Param("value") String value,
                                   @Param("since") Instant since);

    @Query("SELECT DISTINCT t.userId FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.ipAddress = :value AND t.timestamp > :since")
    List<String> customersByIp(@Param("orgId") UUID orgId,
                               @Param("value") String value,
                               @Param("since") Instant since);

    @Query("SELECT DISTINCT t.userId FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.merchantId = :value AND t.timestamp > :since")
    List<String> customersByBeneficiary(@Param("orgId") UUID orgId,
                                        @Param("value") String value,
                                        @Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.userId IN :userIds")
    BigDecimal exposureOf(@Param("orgId") UUID orgId, @Param("userIds") List<String> userIds);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.organizationId = :orgId " +
            "AND t.userId IN :userIds AND t.status = com.sentinel.transaction.domain.model.TransactionStatus.FLAGGED")
    long flaggedCountOf(@Param("orgId") UUID orgId, @Param("userIds") List<String> userIds);
}
