package com.sentinel.scam.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A cooling-off hold. The payment is accepted but not executed until
 * {@code releaseAt}, which gives the customer time to leave the scammer's
 * phone call. Most scam victims who sleep on it do not pay.
 */
@Entity
@Table(name = "payment_holds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentHold {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "beneficiary_id")
    private String beneficiaryId;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "scam_signal_id")
    private UUID scamSignalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HoldStatus status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "release_at", nullable = false)
    private Instant releaseAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "trusted_contact_notified", nullable = false)
    private boolean trustedContactNotified;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
