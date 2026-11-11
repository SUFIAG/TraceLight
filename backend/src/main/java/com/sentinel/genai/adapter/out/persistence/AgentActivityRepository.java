package com.sentinel.genai.adapter.out.persistence;

import com.sentinel.genai.domain.model.AgentActivity;
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
public interface AgentActivityRepository extends JpaRepository<AgentActivity, UUID> {

    Page<AgentActivity> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    List<AgentActivity> findByOrganizationIdAndAgentKeyOrderByCreatedAtDesc(UUID organizationId, String agentKey);

    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM AgentActivity a WHERE a.organizationId = :orgId " +
            "AND a.agentKey = :agentKey AND a.decision = 'ALLOW' AND a.createdAt > :since")
    BigDecimal spentSince(@Param("orgId") UUID orgId,
                          @Param("agentKey") String agentKey,
                          @Param("since") Instant since);

    long countByOrganizationIdAndDecision(UUID organizationId, String decision);
}
