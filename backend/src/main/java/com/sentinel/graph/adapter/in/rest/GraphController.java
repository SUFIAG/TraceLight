package com.sentinel.graph.adapter.in.rest;

import com.sentinel.common.dto.ApiResponse;
import com.sentinel.graph.adapter.out.persistence.EntityLinkRepository;
import com.sentinel.graph.adapter.out.persistence.FraudRingRepository;
import com.sentinel.graph.application.dto.GraphView;
import com.sentinel.graph.application.service.EntityResolutionService;
import com.sentinel.graph.application.service.LinkAnalysisService;
import com.sentinel.graph.application.service.RingDetectionService;
import com.sentinel.graph.domain.model.FraudRing;
import com.sentinel.graph.domain.model.RingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/graph")
@RequiredArgsConstructor
public class GraphController {

    private final EntityResolutionService entityResolutionService;
    private final RingDetectionService ringDetectionService;
    private final LinkAnalysisService linkAnalysisService;
    private final EntityLinkRepository entityLinkRepository;
    private final FraudRingRepository fraudRingRepository;

    /** Rebuilds edges from transaction attributes, then re-detects rings. */
    @PostMapping("/rebuild")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rebuild(Authentication auth) {
        UUID orgId = orgId(auth);
        int pairsProcessed = entityResolutionService.rebuild(orgId);
        List<FraudRing> rings = ringDetectionService.detect(orgId);
        Map<String, Object> result = new HashMap<>();
        // Pairs processed counts every observation, including repeats that only
        // reinforce an existing edge; totalEdges is the distinct relationship count.
        result.put("pairsProcessed", pairsProcessed);
        result.put("edges", entityLinkRepository.countByOrganizationId(orgId));
        result.put("rings", rings.size());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** The force-layout payload the link explorer renders. */
    @GetMapping("/entity/{entityId}")
    public ResponseEntity<ApiResponse<GraphView>> neighbourhood(
            @PathVariable String entityId,
            @RequestParam(defaultValue = "2") int depth,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                linkAnalysisService.neighbourhood(orgId(auth), entityId, depth)));
    }

    @GetMapping("/rings")
    public ResponseEntity<ApiResponse<Page<FraudRing>>> rings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                fraudRingRepository.findByOrganizationIdOrderByRiskScoreDesc(orgId(auth), PageRequest.of(page, size))));
    }

    @PostMapping("/rings/detect")
    public ResponseEntity<ApiResponse<List<FraudRing>>> detect(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(ringDetectionService.detect(orgId(auth))));
    }

    @PatchMapping("/rings/{ringId}/status")
    public ResponseEntity<ApiResponse<FraudRing>> updateRingStatus(
            @PathVariable UUID ringId,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        RingStatus status = RingStatus.valueOf(body.get("status"));
        return ResponseEntity.ok(ApiResponse.success(
                ringDetectionService.updateStatus(orgId(auth), ringId, status)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(Authentication auth) {
        UUID orgId = orgId(auth);
        Map<String, Object> stats = new HashMap<>();
        stats.put("edges", entityLinkRepository.countByOrganizationId(orgId));
        stats.put("detectedRings", fraudRingRepository.countByOrganizationIdAndStatus(orgId, RingStatus.DETECTED));
        stats.put("confirmedRings", fraudRingRepository.countByOrganizationIdAndStatus(orgId, RingStatus.CONFIRMED));
        stats.put("takenDown", fraudRingRepository.countByOrganizationIdAndStatus(orgId, RingStatus.TAKEN_DOWN));
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }
}
