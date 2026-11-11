package com.sentinel.friction.adapter.in.rest;

import com.sentinel.common.dto.ApiResponse;
import com.sentinel.friction.adapter.out.persistence.FrictionDecisionRepository;
import com.sentinel.friction.adapter.out.persistence.FrictionPolicyRepository;
import com.sentinel.friction.application.service.FrictionOrchestrationService;
import com.sentinel.friction.domain.model.FrictionDecision;
import com.sentinel.friction.domain.model.FrictionMethod;
import com.sentinel.friction.domain.model.FrictionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/friction")
@RequiredArgsConstructor
public class FrictionController {

    private final FrictionOrchestrationService frictionService;
    private final FrictionPolicyRepository policyRepository;
    private final FrictionDecisionRepository decisionRepository;

    /** Called inline during authentication or payment confirmation. */
    @PostMapping("/decide")
    public ResponseEntity<ApiResponse<FrictionDecision>> decide(
            @RequestBody Map<String, Object> body, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(frictionService.decide(
                orgId(auth),
                String.valueOf(body.get("userId")),
                body.get("transactionId") == null ? null : UUID.fromString(String.valueOf(body.get("transactionId"))),
                body.get("channel") == null ? "ANY" : String.valueOf(body.get("channel")),
                body.get("riskScore") == null ? 0 : ((Number) body.get("riskScore")).intValue(),
                body.get("amount") == null ? null : new BigDecimal(String.valueOf(body.get("amount"))),
                body.get("deviceFingerprint") == null ? null : String.valueOf(body.get("deviceFingerprint")))));
    }

    @PostMapping("/decisions/{id}/complete")
    public ResponseEntity<ApiResponse<FrictionDecision>> complete(
            @PathVariable UUID id, @RequestBody Map<String, String> body, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                frictionService.complete(orgId(auth), id, body.getOrDefault("outcome", "PASSED"))));
    }

    @GetMapping("/decisions")
    public ResponseEntity<ApiResponse<Page<FrictionDecision>>> decisions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                decisionRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId(auth), PageRequest.of(page, size))));
    }

    @GetMapping("/policies")
    public ResponseEntity<ApiResponse<List<FrictionPolicy>>> policies(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                policyRepository.findByOrganizationIdOrderByPriorityAsc(orgId(auth))));
    }

    @PutMapping("/policies")
    public ResponseEntity<ApiResponse<FrictionPolicy>> savePolicy(
            @RequestBody Map<String, Object> body, Authentication auth) {
        FrictionPolicy incoming = FrictionPolicy.builder()
                .id(body.get("id") == null ? null : UUID.fromString(String.valueOf(body.get("id"))))
                .name(String.valueOf(body.getOrDefault("name", "Unnamed policy")))
                .channel(body.get("channel") == null ? "ANY" : String.valueOf(body.get("channel")))
                .minScore(body.get("minScore") == null ? 0 : ((Number) body.get("minScore")).intValue())
                .maxScore(body.get("maxScore") == null ? 100 : ((Number) body.get("maxScore")).intValue())
                .minAmount(body.get("minAmount") == null ? null
                        : new BigDecimal(String.valueOf(body.get("minAmount"))))
                .method(FrictionMethod.valueOf(String.valueOf(body.getOrDefault("method", "NONE"))))
                .vulnerableOnly(Boolean.TRUE.equals(body.get("vulnerableOnly")))
                .skipIfTrustedDevice(Boolean.TRUE.equals(body.get("skipIfTrustedDevice")))
                .priority(body.get("priority") == null ? 100 : ((Number) body.get("priority")).intValue())
                .enabled(!Boolean.FALSE.equals(body.get("enabled")))
                .build();
        return ResponseEntity.ok(ApiResponse.success(frictionService.savePolicy(orgId(auth), incoming)));
    }

    /** The method catalogue with its abandonment cost, for the policy editor. */
    @GetMapping("/methods")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> methods() {
        List<Map<String, Object>> methods = new ArrayList<>();
        for (FrictionMethod method : FrictionMethod.values()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("method", method.name());
            entry.put("label", method.getLabel());
            entry.put("cost", method.getCost());
            entry.put("abandonmentRate", method.getAbandonmentRate());
            methods.add(entry);
        }
        return ResponseEntity.ok(ApiResponse.success(methods));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(
            @RequestParam(defaultValue = "30") int days, Authentication auth) {
        UUID orgId = orgId(auth);
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        long total = decisionRepository.countByOrganizationIdAndCreatedAtAfter(orgId, since);
        long frictionless = decisionRepository.countByOrganizationIdAndMethodAndCreatedAtAfter(
                orgId, FrictionMethod.NONE, since);

        Map<String, Object> byMethod = new HashMap<>();
        for (Object[] row : decisionRepository.countByMethod(orgId, since)) {
            byMethod.put(((FrictionMethod) row[0]).name(), ((Number) row[1]).longValue());
        }
        Map<String, Object> byOutcome = new HashMap<>();
        for (Object[] row : decisionRepository.countByOutcome(orgId, since)) {
            byOutcome.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("decisions", total);
        stats.put("frictionless", frictionless);
        stats.put("frictionlessRate", total == 0 ? null : Math.round((double) frictionless / total * 1000) / 10.0);
        stats.put("byMethod", byMethod);
        stats.put("byOutcome", byOutcome);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }
}
