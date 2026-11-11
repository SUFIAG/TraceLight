package com.sentinel.scam.application.dto;

import com.sentinel.scam.domain.model.HoldStatus;
import com.sentinel.scam.domain.model.PaymentHold;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentHoldResponse {

    private UUID id;
    private UUID transactionId;
    private String userId;
    private String beneficiaryId;
    private BigDecimal amount;
    private HoldStatus status;
    private String reason;
    private Instant releaseAt;
    private Instant resolvedAt;
    private boolean trustedContactNotified;
    private Instant createdAt;

    public static PaymentHoldResponse from(PaymentHold hold) {
        return PaymentHoldResponse.builder()
                .id(hold.getId())
                .transactionId(hold.getTransactionId())
                .userId(hold.getUserId())
                .beneficiaryId(hold.getBeneficiaryId())
                .amount(hold.getAmount())
                .status(hold.getStatus())
                .reason(hold.getReason())
                .releaseAt(hold.getReleaseAt())
                .resolvedAt(hold.getResolvedAt())
                .trustedContactNotified(hold.isTrustedContactNotified())
                .createdAt(hold.getCreatedAt())
                .build();
    }
}
