package com.sentinel.integration.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An outbound webhook. Secret is used to HMAC-sign every delivery so the receiver can verify origin. */
@Entity
@Table(name = "webhook_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    @Column(name = "target_url", nullable = false, length = 1000)
    private String targetUrl;

    /** ALERT_CREATED, CASE_RESOLVED, SCAM_DETECTED, MULE_CONFIRMED, SAR_FILED, AGENT_ACTION, RING_DETECTED. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> events;

    /** Stored hashed; the plaintext is shown once at creation. */
    @Column(name = "secret_hash", nullable = false)
    private String secretHash;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    /** Consecutive failures past the threshold disable the endpoint rather than retrying forever. */
    @Column(name = "disabled_reason")
    private String disabledReason;

    @Column(name = "last_delivery_at")
    private Instant lastDeliveryAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
