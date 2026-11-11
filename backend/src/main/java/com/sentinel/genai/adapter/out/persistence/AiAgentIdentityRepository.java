package com.sentinel.genai.adapter.out.persistence;

import com.sentinel.genai.domain.model.AiAgentIdentity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiAgentIdentityRepository extends JpaRepository<AiAgentIdentity, UUID> {

    Optional<AiAgentIdentity> findByOrganizationIdAndAgentKey(UUID organizationId, String agentKey);

    Optional<AiAgentIdentity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<AiAgentIdentity> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    List<AiAgentIdentity> findByOrganizationIdAndPrincipalUserId(UUID organizationId, String principalUserId);

    long countByOrganizationIdAndStatus(UUID organizationId, String status);
}
