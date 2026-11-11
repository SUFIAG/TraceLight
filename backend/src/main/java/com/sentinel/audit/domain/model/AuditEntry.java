package com.sentinel.audit.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_trail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /**
     * Structured reasons behind the audited decision.
     *
     * columnDefinition only drives DDL generation, which is disabled here, so it
     * does not tell JDBC how to bind the value. Without the explicit JSON type
     * code the driver sends a varchar and PostgreSQL rejects it against a jsonb
     * column. Every other jsonb mapping in this codebase carries this annotation.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "decision_factors", columnDefinition = "jsonb")
    private Map<String, Object> decisionFactors;

    private String reasoning;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(nullable = false)
    private String result;

    @Column(nullable = false)
    private Instant timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) timestamp = Instant.now();
        if (result == null) result = "SUCCESS";
    }
}
