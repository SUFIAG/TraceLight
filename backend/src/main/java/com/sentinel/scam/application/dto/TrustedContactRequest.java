package com.sentinel.scam.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrustedContactRequest {

    @NotBlank
    private String userId;

    @NotBlank
    private String contactName;

    /** SMS, EMAIL, PUSH, VOICE. */
    @NotBlank
    private String contactChannel;

    @NotBlank
    private String contactAddress;

    private String relationship;
    private BigDecimal notifyAboveAmount;
}
