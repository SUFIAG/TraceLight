package com.sentinel.friction.adapter.out.persistence;

import com.sentinel.friction.domain.model.FrictionDecision;
import com.sentinel.friction.domain.model.FrictionMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FrictionDecisionRepository extends JpaRepository<FrictionDecision, UUID> {

    Page<FrictionDecision> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Optional<FrictionDecision> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByOrganizationIdAndMethodAndCreatedAtAfter(
            UUID organizationId, FrictionMethod method, Instant since);

    long countByOrganizationIdAndCreatedAtAfter(UUID organizationId, Instant since);

    @Query("SELECT d.method, COUNT(d) FROM FrictionDecision d WHERE d.organizationId = :orgId " +
            "AND d.createdAt > :since GROUP BY d.method")
    List<Object[]> countByMethod(@Param("orgId") UUID orgId, @Param("since") Instant since);

    @Query("SELECT d.outcome, COUNT(d) FROM FrictionDecision d WHERE d.organizationId = :orgId " +
            "AND d.createdAt > :since GROUP BY d.outcome")
    List<Object[]> countByOutcome(@Param("orgId") UUID orgId, @Param("since") Instant since);
}
