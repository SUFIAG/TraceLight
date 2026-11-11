package com.sentinel.identity.adapter.out.persistence;

import com.sentinel.identity.domain.model.IdentityVerification;
import com.sentinel.identity.domain.model.VerificationDecision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, UUID> {

    Page<IdentityVerification> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    List<IdentityVerification> findByOrganizationIdAndSubjectIdOrderByCreatedAtDesc(UUID organizationId, String subjectId);

    Page<IdentityVerification> findByOrganizationIdAndDecisionOrderByCreatedAtDesc(
            UUID organizationId, VerificationDecision decision, Pageable pageable);

    List<IdentityVerification> findByNextReviewAtBeforeAndDecisionNot(Instant cutoff, VerificationDecision decision);

    long countByOrganizationIdAndDecision(UUID organizationId, VerificationDecision decision);
}
