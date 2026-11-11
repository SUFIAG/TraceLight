package com.sentinel.aml.adapter.out.persistence;

import com.sentinel.aml.domain.model.SarFiling;
import com.sentinel.aml.domain.model.SarStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SarFilingRepository extends JpaRepository<SarFiling, UUID> {

    Page<SarFiling> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<SarFiling> findByOrganizationIdAndStatusOrderByDeadlineAtAsc(
            UUID organizationId, SarStatus status, Pageable pageable);

    Optional<SarFiling> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<SarFiling> findByOrganizationIdAndDeadlineAtBeforeAndStatusNot(
            UUID organizationId, Instant cutoff, SarStatus status);

    long countByOrganizationIdAndStatus(UUID organizationId, SarStatus status);
}
