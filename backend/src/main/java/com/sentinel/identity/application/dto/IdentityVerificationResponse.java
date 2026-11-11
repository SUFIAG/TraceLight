package com.sentinel.identity.application.dto;

import com.sentinel.identity.domain.model.IdentityVerification;
import com.sentinel.identity.domain.model.SanctionsHit;
import com.sentinel.identity.domain.model.VerificationDecision;
import com.sentinel.identity.domain.model.VerificationType;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdentityVerificationResponse {

    private UUID id;
    private String subjectId;
    private String subjectName;
    private VerificationType type;
    private VerificationDecision decision;
    private int riskScore;
    private int documentScore;
    private int livenessScore;
    private int deepfakeScore;
    private int syntheticScore;
    private int footprintScore;
    private int telcoScore;
    private boolean sanctionsHit;
    private List<String> reasons;
    private List<String> imposedLimits;
    private List<SanctionsHit> screeningHits;
    private Instant nextReviewAt;
    private Instant createdAt;

    public static IdentityVerificationResponse from(IdentityVerification v, List<SanctionsHit> hits) {
        return IdentityVerificationResponse.builder()
                .id(v.getId())
                .subjectId(v.getSubjectId())
                .subjectName(v.getSubjectName())
                .type(v.getType())
                .decision(v.getDecision())
                .riskScore(v.getRiskScore())
                .documentScore(v.getDocumentScore())
                .livenessScore(v.getLivenessScore())
                .deepfakeScore(v.getDeepfakeScore())
                .syntheticScore(v.getSyntheticScore())
                .footprintScore(v.getFootprintScore())
                .telcoScore(v.getTelcoScore())
                .sanctionsHit(v.isSanctionsHit())
                .reasons(v.getReasons())
                .imposedLimits(v.getImposedLimits())
                .screeningHits(hits)
                .nextReviewAt(v.getNextReviewAt())
                .createdAt(v.getCreatedAt())
                .build();
    }
}
