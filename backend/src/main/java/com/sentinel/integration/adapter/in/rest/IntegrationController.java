package com.sentinel.integration.adapter.in.rest;

import com.sentinel.security.application.service.StepUpRequired;
import com.sentinel.common.dto.ApiResponse;
import com.sentinel.integration.adapter.out.persistence.ApiKeyRepository;
import com.sentinel.integration.adapter.out.persistence.WebhookDeliveryRepository;
import com.sentinel.integration.adapter.out.persistence.WebhookSubscriptionRepository;
import com.sentinel.integration.application.service.ApiKeyService;
import com.sentinel.integration.application.service.ConnectorCatalogService;
import com.sentinel.integration.application.service.WebhookService;
import com.sentinel.integration.domain.model.ApiKey;
import com.sentinel.integration.domain.model.WebhookDelivery;
import com.sentinel.integration.domain.model.WebhookSubscription;
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
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
public class IntegrationController {

    private final WebhookService webhookService;
    private final ApiKeyService apiKeyService;
    private final ConnectorCatalogService connectorCatalogService;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final ApiKeyRepository apiKeyRepository;

    /* ---------- Webhooks ---------- */

    @PostMapping("/webhooks")
    public ResponseEntity<ApiResponse<Map<String, Object>>> subscribe(
            @RequestBody Map<String, Object> body, Authentication auth) {
        @SuppressWarnings("unchecked")
        List<String> events = (List<String>) body.getOrDefault("events", List.of());
        return ResponseEntity.ok(ApiResponse.success(webhookService.subscribe(
                orgId(auth),
                String.valueOf(body.getOrDefault("name", "Unnamed webhook")),
                String.valueOf(body.get("targetUrl")),
                events)));
    }

    @GetMapping("/webhooks")
    public ResponseEntity<ApiResponse<List<WebhookSubscription>>> webhooks(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionRepository.findByOrganizationId(orgId(auth))));
    }

    @PatchMapping("/webhooks/{id}")
    public ResponseEntity<ApiResponse<WebhookSubscription>> setActive(
            @PathVariable UUID id, @RequestBody Map<String, Boolean> body, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(webhookService.setActive(
                orgId(auth), id, !Boolean.FALSE.equals(body.get("active")))));
    }

    @GetMapping("/webhooks/deliveries")
    public ResponseEntity<ApiResponse<Page<WebhookDelivery>>> deliveries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                deliveryRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId(auth), PageRequest.of(page, size))));
    }

    /** Publishes a test event so an integrator can verify their endpoint and signature check. */
    @PostMapping("/webhooks/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> test(Authentication auth) {
        int queued = webhookService.publish(orgId(auth), "TEST_EVENT",
                Map.of("message", "Test delivery from the fraud platform"));
        return ResponseEntity.ok(ApiResponse.success(Map.of("queued", queued)));
    }

    /* ---------- API keys ---------- */

    @PostMapping("/api-keys")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    @StepUpRequired
    public ResponseEntity<ApiResponse<Map<String, Object>>> issueKey(
            @RequestBody Map<String, Object> body, Authentication auth) {
        @SuppressWarnings("unchecked")
        List<String> scopes = (List<String>) body.getOrDefault("scopes", List.of("SCORE"));
        return ResponseEntity.ok(ApiResponse.success(apiKeyService.issue(
                orgId(auth),
                String.valueOf(body.getOrDefault("name", "Unnamed key")),
                scopes,
                body.get("rateLimitPerMinute") == null ? null : ((Number) body.get("rateLimitPerMinute")).intValue(),
                body.get("expiryDays") == null ? null : ((Number) body.get("expiryDays")).intValue(),
                actorId(auth))));
    }

    @GetMapping("/api-keys")
    public ResponseEntity<ApiResponse<List<ApiKey>>> keys(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(apiKeyService.list(orgId(auth))));
    }

    @DeleteMapping("/api-keys/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    @StepUpRequired
    public ResponseEntity<ApiResponse<ApiKey>> revokeKey(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(apiKeyService.revoke(orgId(auth), id)));
    }

    /* ---------- Connectors ---------- */

    @GetMapping("/connectors")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> connectors() {
        return ResponseEntity.ok(ApiResponse.success(connectorCatalogService.catalogue()));
    }

    @GetMapping("/export-targets")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> exportTargets() {
        return ResponseEntity.ok(ApiResponse.success(connectorCatalogService.exportTargets()));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(Authentication auth) {
        UUID orgId = orgId(auth);
        Map<String, Object> stats = new HashMap<>();
        stats.put("webhooks", subscriptionRepository.findByOrganizationId(orgId).size());
        stats.put("activeWebhooks", subscriptionRepository.findByOrganizationIdAndActiveTrue(orgId).size());
        stats.put("deliveriesPending", deliveryRepository.countByOrganizationIdAndStatus(orgId, "PENDING"));
        stats.put("deliveriesAbandoned", deliveryRepository.countByOrganizationIdAndStatus(orgId, "ABANDONED"));
        stats.put("apiKeys", apiKeyRepository.findByOrganizationId(orgId).size());
        stats.put("connectors", connectorCatalogService.catalogue().size());
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }

    private UUID actorId(Authentication auth) {
        return UUID.fromString((String) auth.getPrincipal());
    }
}
