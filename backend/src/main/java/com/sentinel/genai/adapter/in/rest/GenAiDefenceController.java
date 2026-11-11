package com.sentinel.genai.adapter.in.rest;

import com.sentinel.common.dto.ApiResponse;
import com.sentinel.genai.adapter.out.persistence.AgentActivityRepository;
import com.sentinel.genai.adapter.out.persistence.AiAgentIdentityRepository;
import com.sentinel.genai.adapter.out.persistence.MediaAnalysisRepository;
import com.sentinel.genai.adapter.out.persistence.PhishingDomainRepository;
import com.sentinel.genai.application.dto.MediaAnalysisRequest;
import com.sentinel.genai.application.service.BrandProtectionService;
import com.sentinel.genai.application.service.DeepfakeDetectionService;
import com.sentinel.genai.application.service.KnowYourAgentService;
import com.sentinel.genai.domain.model.AgentActivity;
import com.sentinel.genai.domain.model.AiAgentIdentity;
import com.sentinel.genai.domain.model.MediaAnalysis;
import com.sentinel.genai.domain.model.PhishingDomain;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Defences against generative-AI-era attacks: synthetic media, unauthorised
 * AI agents, and brand impersonation.
 */
@RestController
@RequestMapping("/api/v1/genai")
@RequiredArgsConstructor
public class GenAiDefenceController {

    private final DeepfakeDetectionService deepfakeDetectionService;
    private final KnowYourAgentService knowYourAgentService;
    private final BrandProtectionService brandProtectionService;
    private final MediaAnalysisRepository mediaRepository;
    private final AiAgentIdentityRepository agentRepository;
    private final AgentActivityRepository agentActivityRepository;
    private final PhishingDomainRepository phishingRepository;

    /* ---------- Synthetic media ---------- */

    @PostMapping("/media/analyse")
    public ResponseEntity<ApiResponse<MediaAnalysis>> analyseMedia(
            @RequestBody @Valid MediaAnalysisRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(deepfakeDetectionService.analyse(orgId(auth), request)));
    }

    @GetMapping("/media")
    public ResponseEntity<ApiResponse<Page<MediaAnalysis>>> media(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String mediaType,
            Authentication auth) {
        UUID orgId = orgId(auth);
        var pageable = PageRequest.of(page, size);
        Page<MediaAnalysis> result = mediaType == null
                ? mediaRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId, pageable)
                : mediaRepository.findByOrganizationIdAndMediaTypeOrderByCreatedAtDesc(orgId, mediaType, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PatchMapping("/media/{id}/review")
    public ResponseEntity<ApiResponse<MediaAnalysis>> reviewMedia(
            @PathVariable UUID id, @RequestBody Map<String, String> body, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(deepfakeDetectionService.review(
                orgId(auth), id, body.get("verdict"), actorId(auth))));
    }

    /* ---------- Know Your Agent ---------- */

    @PostMapping("/agents")
    public ResponseEntity<ApiResponse<AiAgentIdentity>> registerAgent(
            @RequestBody Map<String, Object> body, Authentication auth) {
        @SuppressWarnings("unchecked")
        List<String> scopes = (List<String>) body.getOrDefault("scopes", List.of());
        @SuppressWarnings("unchecked")
        List<String> merchants = (List<String>) body.get("allowedMerchants");

        AiAgentIdentity incoming = AiAgentIdentity.builder()
                .agentKey(String.valueOf(body.get("agentKey")))
                .agentName(String.valueOf(body.getOrDefault("agentName", body.get("agentKey"))))
                .operator(String.valueOf(body.getOrDefault("operator", "UNKNOWN")))
                .principalUserId(String.valueOf(body.get("principalUserId")))
                .keyFingerprint(body.get("publicKey") == null ? null : String.valueOf(body.get("publicKey")))
                .scopes(scopes)
                .allowedMerchants(merchants)
                .perTransactionLimit(body.get("perTransactionLimit") == null ? null
                        : new BigDecimal(String.valueOf(body.get("perTransactionLimit"))))
                .dailyLimit(body.get("dailyLimit") == null ? null
                        : new BigDecimal(String.valueOf(body.get("dailyLimit"))))
                .expiresAt(body.get("expiresAt") == null ? null
                        : Instant.parse(String.valueOf(body.get("expiresAt"))))
                .build();

        return ResponseEntity.ok(ApiResponse.success(knowYourAgentService.register(orgId(auth), incoming)));
    }

    /** Called inline before an agent-initiated action is carried out. */
    @PostMapping("/agents/authorize")
    public ResponseEntity<ApiResponse<KnowYourAgentService.Authorization>> authorizeAgent(
            @RequestBody Map<String, Object> body, Authentication auth) {
        String agentKey = String.valueOf(body.get("agentKey"));
        String scope = String.valueOf(body.getOrDefault("scope", "PAY"));
        String merchantId = body.get("merchantId") == null ? null : String.valueOf(body.get("merchantId"));
        BigDecimal amount = body.get("amount") == null ? null
                : new BigDecimal(String.valueOf(body.get("amount")));
        return ResponseEntity.ok(ApiResponse.success(
                knowYourAgentService.authorize(orgId(auth), agentKey, scope, merchantId, amount)));
    }

    @GetMapping("/agents")
    public ResponseEntity<ApiResponse<Page<AiAgentIdentity>>> agents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                agentRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId(auth), PageRequest.of(page, size))));
    }

    @GetMapping("/agents/{agentKey}/activity")
    public ResponseEntity<ApiResponse<List<AgentActivity>>> agentActivity(
            @PathVariable String agentKey, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                agentActivityRepository.findByOrganizationIdAndAgentKeyOrderByCreatedAtDesc(orgId(auth), agentKey)));
    }

    @PostMapping("/agents/{id}/revoke")
    public ResponseEntity<ApiResponse<AiAgentIdentity>> revokeAgent(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, String> body, Authentication auth) {
        String reason = body == null || body.get("reason") == null ? "Revoked by operator" : body.get("reason");
        return ResponseEntity.ok(ApiResponse.success(knowYourAgentService.revoke(orgId(auth), id, reason)));
    }

    /* ---------- Brand protection ---------- */

    @PostMapping("/brand/assess")
    public ResponseEntity<ApiResponse<List<PhishingDomain>>> assessDomains(
            @RequestBody Map<String, List<String>> body, Authentication auth) {
        List<String> brands = body.getOrDefault("brandTerms", List.of());
        List<String> candidates = body.getOrDefault("domains", List.of());
        return ResponseEntity.ok(ApiResponse.success(
                brandProtectionService.assess(orgId(auth), brands, candidates)));
    }

    @GetMapping("/brand/domains")
    public ResponseEntity<ApiResponse<Page<PhishingDomain>>> domains(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                phishingRepository.findByOrganizationIdOrderBySimilarityScoreDesc(
                        orgId(auth), PageRequest.of(page, size))));
    }

    @PatchMapping("/brand/domains/{id}")
    public ResponseEntity<ApiResponse<PhishingDomain>> updateDomain(
            @PathVariable UUID id, @RequestBody Map<String, Object> body, Authentication auth) {
        UUID orgId = orgId(auth);
        if (body.containsKey("hasLoginForm") || body.containsKey("sslIssued") || body.containsKey("ip")) {
            return ResponseEntity.ok(ApiResponse.success(brandProtectionService.enrich(
                    orgId, id,
                    Boolean.TRUE.equals(body.get("hasLoginForm")),
                    Boolean.TRUE.equals(body.get("sslIssued")),
                    body.get("ip") == null ? null : String.valueOf(body.get("ip")))));
        }
        return ResponseEntity.ok(ApiResponse.success(brandProtectionService.updateStatus(
                orgId, id, String.valueOf(body.get("status")))));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(Authentication auth) {
        UUID orgId = orgId(auth);
        Map<String, Object> stats = new HashMap<>();
        stats.put("syntheticMedia", mediaRepository.countByOrganizationIdAndVerdict(orgId, "SYNTHETIC"));
        stats.put("suspectedSyntheticMedia",
                mediaRepository.countByOrganizationIdAndVerdict(orgId, "SUSPECTED_SYNTHETIC"));
        stats.put("activeAgents", agentRepository.countByOrganizationIdAndStatus(orgId, "ACTIVE"));
        stats.put("revokedAgents", agentRepository.countByOrganizationIdAndStatus(orgId, "REVOKED"));
        stats.put("agentDenials", agentActivityRepository.countByOrganizationIdAndDecision(orgId, "DENY"));
        stats.put("phishingDetected", phishingRepository.countByOrganizationIdAndStatus(orgId, "DETECTED"));
        stats.put("phishingTakenDown", phishingRepository.countByOrganizationIdAndStatus(orgId, "TAKEN_DOWN"));
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }

    private UUID actorId(Authentication auth) {
        return UUID.fromString((String) auth.getPrincipal());
    }
}
