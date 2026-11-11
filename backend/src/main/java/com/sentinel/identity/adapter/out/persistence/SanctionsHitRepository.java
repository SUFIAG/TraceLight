package com.sentinel.identity.adapter.out.persistence;

import com.sentinel.identity.domain.model.SanctionsHit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SanctionsHitRepository extends JpaRepository<SanctionsHit, UUID> {

    List<SanctionsHit> findByOrganizationIdAndSubjectId(UUID organizationId, String subjectId);

    Page<SanctionsHit> findByOrganizationIdAndStatusOrderByMatchScoreDesc(
            UUID organizationId, String status, Pageable pageable);

    Optional<SanctionsHit> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByOrganizationIdAndStatus(UUID organizationId, String status);

    long countByOrganizationIdAndDismissedByAiTrue(UUID organizationId);
}
