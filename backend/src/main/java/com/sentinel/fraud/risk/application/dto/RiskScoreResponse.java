package com.sentinel.fraud.risk.application.dto;

import com.sentinel.fraud.risk.domain.model.RiskScore;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * The risk breakdown behind a transaction decision. The individual factors are
 * exposed so an analyst can see why a score landed where it did, rather than
 * receiving a single opaque number.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskScoreResponse {

    private UUID id;
    private UUID transactionId;
    private int score;
    private String level;
    private int amountRisk;
    private int velocityRisk;
    private int locationRisk;
    private int deviceRisk;
    private int historyRisk;
    private Instant createdAt;

    public static RiskScoreResponse from(RiskScore riskScore) {
        return RiskScoreResponse.builder()
                .id(riskScore.getId())
                .transactionId(riskScore.getTransactionId())
                .score(riskScore.getScore())
                .level(riskScore.getLevel().name())
                .amountRisk(riskScore.getAmountRisk())
                .velocityRisk(riskScore.getVelocityRisk())
                .locationRisk(riskScore.getLocationRisk())
                .deviceRisk(riskScore.getDeviceRisk())
                .historyRisk(riskScore.getHistoryRisk())
                .createdAt(riskScore.getCreatedAt())
                .build();
    }
}
