package com.sentinel.identity.adapter.out.persistence;

import com.sentinel.identity.domain.model.BusinessEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessEntityRepository extends JpaRepository<BusinessEntity, UUID> {

    Optional<BusinessEntity> findByOrganizationIdAndRegistrationNumber(UUID organizationId, String registrationNumber);

    Optional<BusinessEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<BusinessEntity> findByOrganizationIdOrderByRiskScoreDesc(UUID organizationId, Pageable pageable);
}
