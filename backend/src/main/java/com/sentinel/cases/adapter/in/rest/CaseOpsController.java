package com.sentinel.cases.adapter.in.rest;

import com.sentinel.alert.domain.model.AlertStatus;
import com.sentinel.cases.adapter.out.persistence.QaSampleRepository;
import com.sentinel.cases.application.service.CaseOpsService;
import com.sentinel.cases.domain.model.QaSample;
import com.sentinel.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/case-ops")
@RequiredArgsConstructor
public class CaseOpsController {

    private final CaseOpsService caseOpsService;
    private final QaSampleRepository qaSampleRepository;

    @PostMapping("/alerts/bulk-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkStatus(
            @RequestBody Map<String, Object> body, Authentication auth) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.getOrDefault("alertIds", List.of());
        AlertStatus status = AlertStatus.valueOf(String.valueOf(body.getOrDefault("status", "RESOLVED")));
        return ResponseEntity.ok(ApiResponse.success(caseOpsService.bulkUpdateAlerts(
                orgId(auth), ids.stream().map(UUID::fromString).toList(), status, actorId(auth))));
    }

    /** Confirms every beneficiary in a ring in one action. */
    @PostMapping("/rings/takedown")
    public ResponseEntity<ApiResponse<Map<String, Object>>> takedown(
            @RequestBody Map<String, Object> body, Authentication auth) {
        @SuppressWarnings("unchecked")
        List<String> members = (List<String>) body.getOrDefault("members", List.of());
        String source = String.valueOf(body.getOrDefault("source", "RING_TAKEDOWN"));
        return ResponseEntity.ok(ApiResponse.success(caseOpsService.takedownRing(orgId(auth), members, source)));
    }

    @PostMapping("/qa/sample")
    public ResponseEntity<ApiResponse<List<QaSample>>> sample(
            @RequestParam(defaultValue = "20") int size, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(caseOpsService.drawSample(orgId(auth), size)));
    }

    @GetMapping("/qa")
    public ResponseEntity<ApiResponse<Page<QaSample>>> samples(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                qaSampleRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId(auth), PageRequest.of(page, size))));
    }

    @PatchMapping("/qa/{id}")
    public ResponseEntity<ApiResponse<QaSample>> review(
            @PathVariable UUID id, @RequestBody Map<String, Object> body, Authentication auth) {
        @SuppressWarnings("unchecked")
        List<String> findings = (List<String>) body.getOrDefault("findings", List.of());
        return ResponseEntity.ok(ApiResponse.success(caseOpsService.review(
                orgId(auth), id,
                String.valueOf(body.getOrDefault("verdict", "CORRECT")),
                findings,
                body.get("notes") == null ? null : String.valueOf(body.get("notes")),
                actorId(auth))));
    }

    /** Human versus agent accuracy on sampled decisions. */
    @GetMapping("/qa/scorecard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> scorecard(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(caseOpsService.qualityScorecard(orgId(auth))));
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }

    private UUID actorId(Authentication auth) {
        return UUID.fromString((String) auth.getPrincipal());
    }
}
