package com.sentinel.autonomy.adapter.out.persistence;

import com.sentinel.autonomy.domain.model.AttackSimulation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttackSimulationRepository extends JpaRepository<AttackSimulation, UUID> {

    Page<AttackSimulation> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    List<AttackSimulation> findByOrganizationIdAndOutcome(UUID organizationId, String outcome);
}
