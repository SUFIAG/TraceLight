package com.sentinel.autonomy.adapter.out.persistence;

import com.sentinel.autonomy.domain.model.AgentActionType;
import com.sentinel.autonomy.domain.model.AutonomyPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AutonomyPolicyRepository extends JpaRepository<AutonomyPolicy, UUID> {

    Optional<AutonomyPolicy> findByOrganizationIdAndActionType(UUID organizationId, AgentActionType actionType);

    List<AutonomyPolicy> findByOrganizationId(UUID organizationId);
}
