package com.sentinel.demo.adapter.in.rest;

import com.sentinel.common.dto.ApiResponse;
import com.sentinel.demo.application.dto.SeedResponse;
import com.sentinel.demo.application.service.DemoDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoDataService demoDataService;

    /**
     * Generates a demo dataset for the caller's organization by running the real
     * ingestion and fraud analysis pipeline.
     */
    @PostMapping("/seed")
    public ResponseEntity<ApiResponse<SeedResponse>> seed(
            @RequestParam(defaultValue = "false") boolean force,
            Authentication auth) {
        UUID organizationId = UUID.fromString((String) auth.getCredentials());
        UUID userId = UUID.fromString((String) auth.getPrincipal());
        return ResponseEntity.ok(ApiResponse.success(demoDataService.seed(organizationId, userId, force)));
    }
}
