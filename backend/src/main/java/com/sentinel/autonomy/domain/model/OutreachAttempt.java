package com.sentinel.autonomy.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * An automated contact with a customer to verify suspicious activity.
 *
 * <p>Most "is this you?" checks need no analyst at all: the customer confirms
 * or denies and the alert resolves itself. The value is in what the reply
 * closes, so the response and the resulting resolution are both recorded.
 */
@Entity
@Table(name = "outreach_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutreachAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    /** PUSH, SMS, EMAIL, VOICE, IN_APP. */
    @Column(nullable = false, length = 20)
    private String channel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /** PENDING, CONFIRMED_GENUINE, REPORTED_FRAUD, NO_RESPONSE, UNREACHABLE. */
    @Column(nullable = false, length = 30)
    private String outcome;

    @Column(name = "response_text", columnDefinition = "TEXT")
    private String responseText;

    @Column(name = "responded_at")
    private Instant respondedAt;

    /** Deadline after which silence is treated as no response. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
