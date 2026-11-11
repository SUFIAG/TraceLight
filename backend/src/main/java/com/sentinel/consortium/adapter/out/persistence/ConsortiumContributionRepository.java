package com.sentinel.consortium.adapter.out.persistence;

import com.sentinel.consortium.domain.model.ConsortiumContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsortiumContributionRepository extends JpaRepository<ConsortiumContribution, UUID> {

    Optional<ConsortiumContribution> findBySignalIdAndOrganizationId(UUID signalId, UUID organizationId);

    long countBySignalId(UUID signalId);

    long countByOrganizationId(UUID organizationId);

    void deleteByOrganizationId(UUID organizationId);
}
