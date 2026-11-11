package com.sentinel.governance.adapter.in.rest;

import com.sentinel.security.application.service.StepUpRequired;
import com.sentinel.common.dto.ApiResponse;
import com.sentinel.governance.adapter.out.persistence.DriftReportRepository;
import com.sentinel.governance.adapter.out.persistence.FairnessReportRepository;
import com.sentinel.governance.adapter.out.persistence.ModelRegistryRepository;
import com.sentinel.governance.application.service.DriftDetectionService;
import com.sentinel.governance.application.service.FairnessTestingService;
import com.sentinel.governance.application.service.LlmGuardrailService;
import com.sentinel.governance.application.service.ModelRegistryService;
import com.sentinel.governance.domain.model.DriftReport;
import com.sentinel.governance.domain.model.FairnessReport;
import com.sentinel.governance.domain.model.ModelRegistryEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/governance")
@RequiredArgsConstructor
public class GovernanceController {

    private final ModelRegistryService modelRegistryService;
    private final DriftDetectionService driftDetectionService;
    private final FairnessTestingService fairnessTestingService;
    private final LlmGuardrailService guardrailService;
    private final ModelRegistryRepository registryRepository;
    private final DriftReportRepository driftRepository;
    private final FairnessReportRepository fairnessRepository;

    /* ---------- Model registry ---------- */

    @GetMapping("/models")
    public ResponseEntity<ApiResponse<List<ModelRegistryEntry>>> models(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(modelRegistryService.list(orgId(auth))));
    }

    @PostMapping("/models")
    public ResponseEntity<ApiResponse<ModelRegistryEntry>> register(
            @RequestBody Map<String, Object> body, Authentication auth) {
        @SuppressWarnings("unchecked")
        List<String> limitations = (List<String>) body.get("limitations");
        @SuppressWarnings("unchecked")
        List<String> features = (List<String>) body.get("inputFeatures");
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) body.get("metrics");

        ModelRegistryEntry incoming = ModelRegistryEntry.builder()
                .modelName(String.valueOf(body.get("modelName")))
                .modelVersion(String.valueOf(body.getOrDefault("modelVersion", "1.0.0")))
                .modelType(String.valueOf(body.getOrDefault("modelType", "SCORECARD")))
                .status(body.get("status") == null ? null : String.valueOf(body.get("status")))
                .riskTier(body.get("riskTier") == null ? null : String.valueOf(body.get("riskTier")))
                .purpose(body.get("purpose") == null ? null : String.valueOf(body.get("purpose")))
                .ownerName(String.valueOf(body.getOrDefault("ownerName", "Unassigned")))
                .trainingData(body.get("trainingData") == null ? null : String.valueOf(body.get("trainingData")))
                .metrics(metrics)
                .limitations(limitations)
                .inputFeatures(features)
                .build();

        return ResponseEntity.ok(ApiResponse.success(modelRegistryService.register(orgId(auth), incoming)));
    }

    @PostMapping("/models/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    @StepUpRequired
    public ResponseEntity<ApiResponse<ModelRegistryEntry>> approve(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, String> body, Authentication auth) {
        String validatedBy = body == null ? null : body.get("validatedBy");
        return ResponseEntity.ok(ApiResponse.success(
                modelRegistryService.approve(orgId(auth), id, actorId(auth), validatedBy)));
    }

    @PatchMapping("/models/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    @StepUpRequired
    public ResponseEntity<ApiResponse<ModelRegistryEntry>> setStatus(
            @PathVariable UUID id, @RequestBody Map<String, String> body, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                modelRegistryService.setStatus(orgId(auth), id, body.get("status"))));
    }

    /** Regulator-ready documentation, generated from the live registry. */
    @GetMapping("/models/{id}/documentation")
    public ResponseEntity<ApiResponse<Map<String, String>>> documentation(
            @PathVariable UUID id, Authentication auth) {
        String doc = modelRegistryService.documentationFor(orgId(auth), id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("documentation", doc)));
    }

    /* ---------- Drift ---------- */

    @PostMapping("/drift/detect")
    public ResponseEntity<ApiResponse<DriftReport>> detectDrift(
            @RequestParam(defaultValue = "RiskScoringService") String model,
            @RequestParam(defaultValue = "60") int baselineDays,
            @RequestParam(defaultValue = "14") int currentDays,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                driftDetectionService.detect(orgId(auth), model, baselineDays, currentDays)));
    }

    @GetMapping("/drift")
    public ResponseEntity<ApiResponse<Page<DriftReport>>> drift(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                driftRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId(auth), PageRequest.of(page, size))));
    }

    /* ---------- Fairness ---------- */

    @PostMapping("/fairness/test")
    public ResponseEntity<ApiResponse<FairnessReport>> testFairness(
            @RequestParam(defaultValue = "RiskScoringService") String model,
            @RequestParam(defaultValue = "COUNTRY") String attribute,
            @RequestParam(defaultValue = "90") int days,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                fairnessTestingService.test(orgId(auth), model, attribute, days)));
    }

    @GetMapping("/fairness")
    public ResponseEntity<ApiResponse<Page<FairnessReport>>> fairness(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                fairnessRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId(auth), PageRequest.of(page, size))));
    }

    /* ---------- Guardrails ---------- */

    /** Shows exactly what would be stripped from a prompt before it leaves. */
    @PostMapping("/guardrails/redact")
    public ResponseEntity<ApiResponse<LlmGuardrailService.Redaction>> redact(
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(guardrailService.redact(body.get("prompt"))));
    }

    /** Checks an AI narrative against the evidence it was given. */
    @PostMapping("/guardrails/verify")
    public ResponseEntity<ApiResponse<LlmGuardrailService.Verification>> verify(
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> evidence = (List<String>) body.getOrDefault("evidence", List.of());
        return ResponseEntity.ok(ApiResponse.success(
                guardrailService.verify(String.valueOf(body.get("narrative")), evidence)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(Authentication auth) {
        UUID orgId = orgId(auth);
        Map<String, Object> stats = new HashMap<>();
        stats.put("modelsInProduction", registryRepository.countByOrganizationIdAndStatus(orgId, "PRODUCTION"));
        stats.put("modelsAwaitingApproval", registryRepository.countByOrganizationIdAndStatus(orgId, "VALIDATION"));
        stats.put("driftedModels", driftRepository.findByOrganizationIdAndStatus(orgId, "DRIFTED").size());
        stats.put("fairnessFailures", fairnessRepository.findByOrganizationIdAndVerdict(orgId, "FAIL").size());
        stats.put("fairnessReviews", fairnessRepository.findByOrganizationIdAndVerdict(orgId, "REVIEW").size());
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }

    private UUID actorId(Authentication auth) {
        return UUID.fromString((String) auth.getPrincipal());
    }
}
