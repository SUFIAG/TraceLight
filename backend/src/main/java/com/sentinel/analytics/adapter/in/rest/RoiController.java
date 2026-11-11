package com.sentinel.analytics.adapter.in.rest;

import com.sentinel.analytics.application.service.LossForecastService;
import com.sentinel.analytics.application.service.RoiService;
import com.sentinel.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roi")
@RequiredArgsConstructor
public class RoiController {

    private final RoiService roiService;
    private final LossForecastService lossForecastService;

    /** Fraud stopped, hours returned, false positives avoided, customers not interrupted. */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary(
            @RequestParam(defaultValue = "30") int days, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(roiService.summary(orgId(auth), days)));
    }

    /** Short-horizon projection of flagged volume, with the calendar drivers behind it. */
    @GetMapping("/forecast")
    public ResponseEntity<ApiResponse<Map<String, Object>>> forecast(
            @RequestParam(defaultValue = "60") int historyDays,
            @RequestParam(defaultValue = "14") int horizonDays,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                lossForecastService.forecast(orgId(auth), historyDays, horizonDays)));
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }
}
