package com.sentinel.scam.adapter.out.persistence;

import com.sentinel.scam.domain.model.TrustedContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrustedContactRepository extends JpaRepository<TrustedContact, UUID> {

    List<TrustedContact> findByOrganizationIdAndUserIdAndActiveTrue(UUID organizationId, String userId);
}
