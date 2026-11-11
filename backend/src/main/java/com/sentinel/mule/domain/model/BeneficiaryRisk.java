package com.sentinel.mule.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Risk held against the account that <em>receives</em> money.
 *
 * <p>Classic fraud detection scores the payer. Scam money, however, always
 * lands somewhere, and the receiving account is the part of the picture that
 * repeats across victims. A beneficiary with forty unrelated senders in a week
 * is a mule whichever customer is paying it.
 */
@Entity
@Table(name = "beneficiary_risk", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organization_id", "beneficiary_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeneficiaryRisk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** The receiving account key. Maps to {@code transactions.merchant_id} for payment rails. */
    @Column(name = "beneficiary_id", nullable = false)
    private String beneficiaryId;

    @Column(name = "beneficiary_name")
    private String beneficiaryName;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MuleBand band;

    /** Distinct unrelated payers in the observation window. The single strongest mule signal. */
    @Column(name = "fan_in", nullable = false)
    private int fanIn;

    @Column(name = "credit_count", nullable = false)
    private int creditCount;

    @Column(name = "total_credited", precision = 18, scale = 2)
    private BigDecimal totalCredited;

    /** Share of credits under the small-value threshold — the smurfing fingerprint. */
    @Column(name = "small_credit_ratio", nullable = false)
    private double smallCreditRatio;

    /** Days between the first and most recent credit. Short windows with high fan-in are mules. */
    @Column(name = "activity_window_days", nullable = false)
    private int activityWindowDays;

    @Column(name = "distinct_countries", nullable = false)
    private int distinctCountries;

    /** Set when another institution or the consortium has confirmed this account. */
    @Column(name = "externally_confirmed", nullable = false)
    private boolean externallyConfirmed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> reasons;

    @Column(name = "first_seen")
    private Instant firstSeen;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
