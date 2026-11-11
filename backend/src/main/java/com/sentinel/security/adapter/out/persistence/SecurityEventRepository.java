package com.sentinel.security.adapter.out.persistence;

import com.sentinel.security.domain.model.SecurityEvent;
import com.sentinel.security.domain.model.SecurityEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {

    Page<SecurityEvent> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<SecurityEvent> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<SecurityEvent> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByOrganizationIdAndEventTypeAndCreatedAtAfter(
            UUID organizationId, SecurityEventType eventType, Instant after);

    long countByOrganizationIdAndCreatedAtAfter(UUID organizationId, Instant after);
}
