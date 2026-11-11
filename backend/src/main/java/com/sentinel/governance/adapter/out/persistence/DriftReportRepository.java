package com.sentinel.governance.adapter.out.persistence;

import com.sentinel.governance.domain.model.DriftReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DriftReportRepository extends JpaRepository<DriftReport, UUID> {

    Page<DriftReport> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    List<DriftReport> findByOrganizationIdAndStatus(UUID organizationId, String status);
}
