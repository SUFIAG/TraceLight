package com.sentinel.genai.adapter.out.persistence;

import com.sentinel.genai.domain.model.MediaAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MediaAnalysisRepository extends JpaRepository<MediaAnalysis, UUID> {

    Page<MediaAnalysis> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<MediaAnalysis> findByOrganizationIdAndMediaTypeOrderByCreatedAtDesc(
            UUID organizationId, String mediaType, Pageable pageable);

    List<MediaAnalysis> findByOrganizationIdAndSubjectId(UUID organizationId, String subjectId);

    Optional<MediaAnalysis> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByOrganizationIdAndVerdict(UUID organizationId, String verdict);
}
