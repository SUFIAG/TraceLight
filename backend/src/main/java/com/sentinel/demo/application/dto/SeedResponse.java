package com.sentinel.demo.application.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeedResponse {

    private int transactionsCreated;
    private int transactionsFlagged;
    private int alertsCreated;
    private int casesCreated;
    private boolean skipped;
    private String message;
}
