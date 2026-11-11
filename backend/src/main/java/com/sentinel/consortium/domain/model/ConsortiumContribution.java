package com.sentinel.consortium.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Which institution contributed which signal.
 *
 * <p>Held separately from the signal itself so that reading the shared table
 * never reveals who reported what. This table exists for two narrow purposes:
 * counting distinct contributors for the k-anonymity gate, and letting a
 * contributor withdraw its own data.
 */
@Entity
@Table(name = "consortium_contributions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"signal_id", "organization_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsortiumContribution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "signal_id", nullable = false)
    private UUID signalId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private int severity;

    @Column(name = "report_count", nullable = false)
    private int reportCount;

    @Column(name = "last_reported_at", nullable = false)
    private Instant lastReportedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
