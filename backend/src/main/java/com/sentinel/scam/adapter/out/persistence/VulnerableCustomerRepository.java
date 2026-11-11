package com.sentinel.scam.adapter.out.persistence;

import com.sentinel.scam.domain.model.VulnerableCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VulnerableCustomerRepository extends JpaRepository<VulnerableCustomer, UUID> {

    Optional<VulnerableCustomer> findByOrganizationIdAndUserIdAndActiveTrue(UUID organizationId, String userId);

    List<VulnerableCustomer> findByOrganizationIdAndActiveTrue(UUID organizationId);
}
