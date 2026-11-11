package com.sentinel.aml.adapter.out.persistence;

import com.sentinel.aml.domain.model.AmlFinding;
import com.sentinel.aml.domain.model.AmlTypology;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AmlFindingRepository extends JpaRepository<AmlFinding, UUID> {

    Page<AmlFinding> findByOrganizationIdOrderByScoreDesc(UUID organizationId, Pageable pageable);

    Page<AmlFinding> findByOrganizationIdAndStatusOrderByScoreDesc(UUID organizationId, String status, Pageable pageable);

    List<AmlFinding> findByOrganizationIdAndSubjectIdOrderByCreatedAtDesc(UUID organizationId, String subjectId);

    Optional<AmlFinding> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("SELECT f FROM AmlFinding f WHERE f.organizationId = :orgId AND f.subjectId = :subjectId " +
            "AND f.typology = :typology AND f.windowEnd > :since")
    Optional<AmlFinding> findRecent(@Param("orgId") UUID orgId,
                                    @Param("subjectId") String subjectId,
                                    @Param("typology") AmlTypology typology,
                                    @Param("since") Instant since);

    @Query("SELECT f.typology, COUNT(f) FROM AmlFinding f WHERE f.organizationId = :orgId " +
            "GROUP BY f.typology ORDER BY COUNT(f) DESC")
    List<Object[]> countByTypology(@Param("orgId") UUID orgId);

    long countByOrganizationIdAndStatus(UUID organizationId, String status);
}
