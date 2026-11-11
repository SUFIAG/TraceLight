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
 * One agent's work on one item: what it looked at, what it concluded, what it
 * did, and what stopped it from doing more.
 *
 * <p>{@code suppressedReason} is the field that makes autonomy auditable. When
 * an agent wanted to close an alert but policy forbade it, that is recorded
 * explicitly rather than appearing as an agent that simply did nothing. Over a
 * quarter, those records are the evidence for raising or lowering a limit.
 */
@Entity
@Table(name = "agent_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** TRIAGE, INVESTIGATOR, OUTREACH, RED_TEAM, RULE_DISCOVERY, DISPUTE, TUNING. */
    @Column(name = "agent_name", nullable = false, length = 40)
    private String agentName;

    /** ALERT, CASE, TRANSACTION, BENEFICIARY, RULE, CONTROL. */
    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AgentVerdict verdict;

    /** 0-100 in the agent's own verdict. Drives whether policy lets it act. */
    @Column(nullable = false)
    private int confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposed_action", length = 40)
    private AgentActionType proposedAction;

    @Column(name = "action_executed", nullable = false)
    private boolean actionExecuted;

    @Column(name = "suppressed_reason")
    private String suppressedReason;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rationale;

    /** Each claim the agent made, with the source it came from. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> evidence;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    /** True when a language model contributed; false for deterministic reasoning. */
    @Column(name = "llm_assisted", nullable = false)
    private boolean llmAssisted;

    /** Set when a human later agreed or disagreed. The training signal. */
    @Column(name = "human_agreed")
    private Boolean humanAgreed;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
