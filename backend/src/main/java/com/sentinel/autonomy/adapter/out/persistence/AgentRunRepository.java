package com.sentinel.autonomy.adapter.out.persistence;

import com.sentinel.autonomy.domain.model.AgentRun;
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
public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {

    Page<AgentRun> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<AgentRun> findByOrganizationIdAndAgentNameOrderByCreatedAtDesc(
            UUID organizationId, String agentName, Pageable pageable);

    List<AgentRun> findByOrganizationIdAndTargetIdOrderByCreatedAtDesc(UUID organizationId, String targetId);

    Optional<AgentRun> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("SELECT COUNT(r) FROM AgentRun r WHERE r.organizationId = :orgId AND r.proposedAction = :action " +
            "AND r.actionExecuted = true AND r.createdAt > :since")
    long countExecutedSince(@Param("orgId") UUID orgId,
                            @Param("action") com.sentinel.autonomy.domain.model.AgentActionType action,
                            @Param("since") Instant since);

    @Query("SELECT COUNT(r) FROM AgentRun r WHERE r.organizationId = :orgId AND r.createdAt > :since")
    long countSince(@Param("orgId") UUID orgId, @Param("since") Instant since);

    @Query("SELECT COUNT(r) FROM AgentRun r WHERE r.organizationId = :orgId " +
            "AND r.actionExecuted = true AND r.createdAt > :since")
    long countAutomatedSince(@Param("orgId") UUID orgId, @Param("since") Instant since);

    @Query("SELECT COUNT(r) FROM AgentRun r WHERE r.organizationId = :orgId AND r.humanAgreed = true")
    long countAgreed(@Param("orgId") UUID orgId);

    @Query("SELECT COUNT(r) FROM AgentRun r WHERE r.organizationId = :orgId AND r.humanAgreed IS NOT NULL")
    long countReviewed(@Param("orgId") UUID orgId);

    @Query("SELECT COALESCE(AVG(r.durationMs), 0) FROM AgentRun r WHERE r.organizationId = :orgId " +
            "AND r.createdAt > :since")
    double averageDurationSince(@Param("orgId") UUID orgId, @Param("since") Instant since);
}
