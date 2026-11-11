package com.sentinel.autonomy.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One red-team run: a synthetic attack pattern replayed against the live
 * control stack to see whether it would have been caught.
 *
 * <p>Controls degrade silently. A rule written for a 2023 card-testing pattern
 * stops firing when the attacker changes the amount band, and nobody notices
 * because the absence of alerts looks like success. Continuously attacking
 * your own controls is the only way that failure becomes visible before a
 * criminal finds it.
 */
@Entity
@Table(name = "attack_simulations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttackSimulation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "scenario_key", nullable = false, length = 60)
    private String scenarioKey;

    @Column(name = "scenario_name", nullable = false)
    private String scenarioName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "transactions_simulated", nullable = false)
    private int transactionsSimulated;

    @Column(name = "detected_count", nullable = false)
    private int detectedCount;

    /** Share of the synthetic attack the live controls would have caught. */
    @Column(name = "detection_rate", nullable = false)
    private double detectionRate;

    /** Highest risk score any of the simulated transactions reached. */
    @Column(name = "peak_score", nullable = false)
    private int peakScore;

    /** PASS, PARTIAL, FAIL. */
    @Column(nullable = false, length = 20)
    private String outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> gaps;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> recommendations;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
