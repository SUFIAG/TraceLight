package com.sentinel.genai.adapter.out.persistence;

import com.sentinel.genai.domain.model.PhishingDomain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhishingDomainRepository extends JpaRepository<PhishingDomain, UUID> {

    Optional<PhishingDomain> findByOrganizationIdAndDomain(UUID organizationId, String domain);

    Optional<PhishingDomain> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<PhishingDomain> findByOrganizationIdOrderBySimilarityScoreDesc(UUID organizationId, Pageable pageable);

    long countByOrganizationIdAndStatus(UUID organizationId, String status);
}
