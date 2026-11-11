package com.sentinel.mule.application.dto;

import com.sentinel.mule.domain.model.BeneficiaryRisk;
import com.sentinel.mule.domain.model.MuleBand;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeneficiaryRiskResponse {

    private UUID id;
    private String beneficiaryId;
    private String beneficiaryName;
    private int score;
    private MuleBand band;
    private int fanIn;
    private int creditCount;
    private BigDecimal totalCredited;
    private double smallCreditRatio;
    private int activityWindowDays;
    private int distinctCountries;
    private boolean externallyConfirmed;
    private List<String> reasons;
    private Instant firstSeen;
    private Instant lastSeen;

    public static BeneficiaryRiskResponse from(BeneficiaryRisk risk) {
        return BeneficiaryRiskResponse.builder()
                .id(risk.getId())
                .beneficiaryId(risk.getBeneficiaryId())
                .beneficiaryName(risk.getBeneficiaryName())
                .score(risk.getScore())
                .band(risk.getBand())
                .fanIn(risk.getFanIn())
                .creditCount(risk.getCreditCount())
                .totalCredited(risk.getTotalCredited())
                .smallCreditRatio(risk.getSmallCreditRatio())
                .activityWindowDays(risk.getActivityWindowDays())
                .distinctCountries(risk.getDistinctCountries())
                .externallyConfirmed(risk.isExternallyConfirmed())
                .reasons(risk.getReasons())
                .firstSeen(risk.getFirstSeen())
                .lastSeen(risk.getLastSeen())
                .build();
    }
}
