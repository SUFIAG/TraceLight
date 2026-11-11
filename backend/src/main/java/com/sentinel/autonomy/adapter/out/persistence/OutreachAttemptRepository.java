package com.sentinel.autonomy.adapter.out.persistence;

import com.sentinel.autonomy.domain.model.OutreachAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutreachAttemptRepository extends JpaRepository<OutreachAttempt, UUID> {

    Page<OutreachAttempt> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    List<OutreachAttempt> findByOrganizationIdAndOutcome(UUID organizationId, String outcome);

    Optional<OutreachAttempt> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByOrganizationIdAndOutcome(UUID organizationId, String outcome);
}
