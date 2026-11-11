package com.sentinel.disputes.adapter.out.persistence;

import com.sentinel.disputes.domain.model.Dispute;
import com.sentinel.disputes.domain.model.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    Page<Dispute> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<Dispute> findByOrganizationIdAndStatusOrderByRespondByAsc(
            UUID organizationId, DisputeStatus status, Pageable pageable);

    Optional<Dispute> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Dispute> findByOrganizationIdAndUserId(UUID organizationId, String userId);

    long countByOrganizationIdAndStatus(UUID organizationId, DisputeStatus status);

    List<Dispute> findByOrganizationIdAndRespondByBeforeAndStatusNotIn(
            UUID organizationId, Instant cutoff, List<DisputeStatus> excluded);

    @Query("SELECT COALESCE(SUM(d.recoveredAmount), 0) FROM Dispute d " +
            "WHERE d.organizationId = :orgId AND d.status = com.sentinel.disputes.domain.model.DisputeStatus.WON")
    BigDecimal totalRecovered(@Param("orgId") UUID orgId);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Dispute d WHERE d.organizationId = :orgId " +
            "AND d.createdAt > :since")
    BigDecimal totalDisputed(@Param("orgId") UUID orgId, @Param("since") Instant since);
}
