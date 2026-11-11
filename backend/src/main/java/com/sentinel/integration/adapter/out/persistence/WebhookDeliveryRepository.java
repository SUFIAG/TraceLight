package com.sentinel.integration.adapter.out.persistence;

import com.sentinel.integration.domain.model.WebhookDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    Page<WebhookDelivery> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    List<WebhookDelivery> findByStatusAndNextAttemptAtBefore(String status, Instant cutoff);

    long countByOrganizationIdAndStatus(UUID organizationId, String status);
}
