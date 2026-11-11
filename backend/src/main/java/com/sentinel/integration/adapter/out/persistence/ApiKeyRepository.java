package com.sentinel.integration.adapter.out.persistence;

import com.sentinel.integration.domain.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findByOrganizationId(UUID organizationId);

    Optional<ApiKey> findByKeyHash(String keyHash);

    Optional<ApiKey> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
