package com.sentinel.governance.adapter.out.persistence;

import com.sentinel.governance.domain.model.ModelRegistryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModelRegistryRepository extends JpaRepository<ModelRegistryEntry, UUID> {

    List<ModelRegistryEntry> findByOrganizationIdOrderByModelNameAscModelVersionDesc(UUID organizationId);

    Optional<ModelRegistryEntry> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<ModelRegistryEntry> findByOrganizationIdAndModelNameAndModelVersion(
            UUID organizationId, String modelName, String modelVersion);

    List<ModelRegistryEntry> findByOrganizationIdAndStatus(UUID organizationId, String status);

    long countByOrganizationIdAndStatus(UUID organizationId, String status);
}
