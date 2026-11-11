package com.sentinel.strategy.adapter.out.persistence;

import com.sentinel.strategy.domain.model.RuleDefinition;
import com.sentinel.strategy.domain.model.RuleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RuleDefinitionRepository extends JpaRepository<RuleDefinition, UUID> {

    Page<RuleDefinition> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    List<RuleDefinition> findByOrganizationIdAndStatus(UUID organizationId, RuleStatus status);

    List<RuleDefinition> findByOrganizationIdAndStatusIn(UUID organizationId, List<RuleStatus> statuses);

    Optional<RuleDefinition> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<RuleDefinition> findByOrganizationIdAndNameOrderByVersionDesc(UUID organizationId, String name);

    long countByOrganizationIdAndStatus(UUID organizationId, RuleStatus status);
}
