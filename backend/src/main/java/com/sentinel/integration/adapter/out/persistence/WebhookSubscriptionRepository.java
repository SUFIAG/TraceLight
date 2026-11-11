package com.sentinel.integration.adapter.out.persistence;

import com.sentinel.integration.domain.model.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {

    List<WebhookSubscription> findByOrganizationIdAndActiveTrue(UUID organizationId);

    List<WebhookSubscription> findByOrganizationId(UUID organizationId);

    Optional<WebhookSubscription> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
