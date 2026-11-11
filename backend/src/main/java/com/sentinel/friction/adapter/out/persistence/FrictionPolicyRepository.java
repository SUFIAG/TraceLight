package com.sentinel.friction.adapter.out.persistence;

import com.sentinel.friction.domain.model.FrictionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FrictionPolicyRepository extends JpaRepository<FrictionPolicy, UUID> {

    List<FrictionPolicy> findByOrganizationIdAndEnabledTrueOrderByPriorityAsc(UUID organizationId);

    List<FrictionPolicy> findByOrganizationIdOrderByPriorityAsc(UUID organizationId);

    Optional<FrictionPolicy> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
