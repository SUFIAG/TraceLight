package com.sentinel.graph.adapter.out.persistence;

import com.sentinel.graph.domain.model.FraudRing;
import com.sentinel.graph.domain.model.RingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FraudRingRepository extends JpaRepository<FraudRing, UUID> {

    Optional<FraudRing> findByOrganizationIdAndRingKey(UUID organizationId, String ringKey);

    Optional<FraudRing> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<FraudRing> findByOrganizationIdOrderByRiskScoreDesc(UUID organizationId, Pageable pageable);

    List<FraudRing> findByOrganizationIdAndStatus(UUID organizationId, RingStatus status);

    long countByOrganizationIdAndStatus(UUID organizationId, RingStatus status);
}
