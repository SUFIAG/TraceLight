package com.sentinel.governance.adapter.out.persistence;

import com.sentinel.governance.domain.model.FairnessReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FairnessReportRepository extends JpaRepository<FairnessReport, UUID> {

    Page<FairnessReport> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    List<FairnessReport> findByOrganizationIdAndVerdict(UUID organizationId, String verdict);
}
