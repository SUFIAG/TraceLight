package com.sentinel.scam.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A person the customer nominates to be told about high-risk payments.
 * Valuable for elderly and vulnerable customers, who are the primary targets
 * of impersonation and romance scams.
 */
@Entity
@Table(name = "trusted_contacts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrustedContact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(name = "contact_channel", nullable = false, length = 20)
    private String contactChannel;

    /** Stored masked. The platform never needs the full destination to prove it notified. */
    @Column(name = "contact_address", nullable = false)
    private String contactAddress;

    private String relationship;

    /** Below this amount the contact is not bothered. */
    @Column(name = "notify_above_amount", precision = 15, scale = 2)
    private BigDecimal notifyAboveAmount;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
