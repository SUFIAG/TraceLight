package com.sentinel.scam.application.dto;

import com.sentinel.scam.domain.model.ScamAction;
import com.sentinel.scam.domain.model.ScamTypology;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScamAssessmentResponse {

    private UUID signalId;
    private int score;
    private int coachingScore;
    private int beneficiaryScore;
    private int vulnerabilityScore;
    private ScamTypology typology;
    private String typologyLabel;
    private ScamAction action;
    private List<String> indicators;

    /** Typology-specific copy to show the customer. Null when action is ALLOW. */
    private String warning;

    private UUID holdId;
    private Instant releaseAt;
    private boolean trustedContactNotified;
}
