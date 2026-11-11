package com.sentinel.cases.adapter.out.persistence;

import com.sentinel.cases.domain.model.QaSample;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QaSampleRepository extends JpaRepository<QaSample, UUID> {

    Page<QaSample> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Optional<QaSample> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByOrganizationIdAndVerdict(UUID organizationId, String verdict);

    @Query("SELECT q.decidedByType, q.verdict, COUNT(q) FROM QaSample q " +
            "WHERE q.organizationId = :orgId AND q.verdict <> 'PENDING' " +
            "GROUP BY q.decidedByType, q.verdict")
    List<Object[]> accuracyBreakdown(@Param("orgId") UUID orgId);
}
