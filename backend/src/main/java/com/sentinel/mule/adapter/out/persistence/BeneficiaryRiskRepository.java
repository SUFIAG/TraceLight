package com.sentinel.mule.adapter.out.persistence;

import com.sentinel.mule.domain.model.BeneficiaryRisk;
import com.sentinel.mule.domain.model.MuleBand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BeneficiaryRiskRepository extends JpaRepository<BeneficiaryRisk, UUID> {

    Optional<BeneficiaryRisk> findByOrganizationIdAndBeneficiaryId(UUID organizationId, String beneficiaryId);

    Page<BeneficiaryRisk> findByOrganizationIdOrderByScoreDesc(UUID organizationId, Pageable pageable);

    List<BeneficiaryRisk> findByOrganizationIdAndBandOrderByScoreDesc(UUID organizationId, MuleBand band);

    long countByOrganizationIdAndBand(UUID organizationId, MuleBand band);
}
