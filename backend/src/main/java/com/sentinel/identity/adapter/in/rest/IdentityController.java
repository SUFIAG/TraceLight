package com.sentinel.identity.adapter.in.rest;

import com.sentinel.common.dto.ApiResponse;
import com.sentinel.identity.adapter.out.persistence.IdentityVerificationRepository;
import com.sentinel.identity.adapter.out.persistence.SanctionsHitRepository;
import com.sentinel.identity.application.dto.IdentityVerificationResponse;
import com.sentinel.identity.application.dto.VerifyIdentityRequest;
import com.sentinel.identity.application.service.IdentityVerificationService;
import com.sentinel.identity.application.service.PerpetualKycService;
import com.sentinel.identity.application.service.SanctionsScreeningService;
import com.sentinel.identity.domain.model.IdentityVerification;
import com.sentinel.identity.domain.model.SanctionsHit;
import com.sentinel.identity.domain.model.VerificationDecision;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/identity")
@RequiredArgsConstructor
public class IdentityController {

    private final IdentityVerificationService verificationService;
    private final SanctionsScreeningService sanctionsScreening;
    private final PerpetualKycService perpetualKycService;
    private final IdentityVerificationRepository verificationRepository;
    private final SanctionsHitRepository sanctionsHitRepository;

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<IdentityVerificationResponse>> verify(
            @RequestBody @Valid VerifyIdentityRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(verificationService.verify(orgId(auth), request)));
    }

    @GetMapping("/verifications")
    public ResponseEntity<ApiResponse<Page<IdentityVerification>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) VerificationDecision decision,
            Authentication auth) {
        UUID orgId = orgId(auth);
        var pageable = PageRequest.of(page, size);
        Page<IdentityVerification> result = decision == null
                ? verificationRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId, pageable)
                : verificationRepository.findByOrganizationIdAndDecisionOrderByCreatedAtDesc(orgId, decision, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/subjects/{subjectId}")
    public ResponseEntity<ApiResponse<List<IdentityVerification>>> history(
            @PathVariable String subjectId, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                verificationRepository.findByOrganizationIdAndSubjectIdOrderByCreatedAtDesc(orgId(auth), subjectId)));
    }

    @PatchMapping("/verifications/{id}/decision")
    public ResponseEntity<ApiResponse<IdentityVerification>> override(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        VerificationDecision decision = VerificationDecision.valueOf(body.get("decision"));
        return ResponseEntity.ok(ApiResponse.success(
                verificationService.override(orgId(auth), id, decision, actorId(auth))));
    }

    /* ---------- Screening ---------- */

    @GetMapping("/screening")
    public ResponseEntity<ApiResponse<Page<SanctionsHit>>> screeningQueue(
            @RequestParam(defaultValue = "OPEN") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                sanctionsHitRepository.findByOrganizationIdAndStatusOrderByMatchScoreDesc(
                        orgId(auth), status, PageRequest.of(page, size))));
    }

    @PatchMapping("/screening/{hitId}")
    public ResponseEntity<ApiResponse<SanctionsHit>> decideHit(
            @PathVariable UUID hitId,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(sanctionsScreening.decide(
                orgId(auth), hitId, body.getOrDefault("status", "CONFIRMED"), actorId(auth), body.get("note"))));
    }

    /* ---------- Perpetual KYC ---------- */

    @GetMapping("/reviews/due")
    public ResponseEntity<ApiResponse<List<IdentityVerification>>> dueReviews(Authentication auth) {
        UUID orgId = orgId(auth);
        List<IdentityVerification> due = perpetualKycService.due().stream()
                .filter(v -> v.getOrganizationId().equals(orgId))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(due));
    }

    @PostMapping("/reviews/{subjectId}/trigger")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerReview(
            @PathVariable String subjectId,
            @RequestParam(defaultValue = "MANUAL") String trigger,
            Authentication auth) {
        int updated = perpetualKycService.triggerReview(orgId(auth), subjectId, trigger);
        return ResponseEntity.ok(ApiResponse.success(Map.of("subjectId", subjectId, "updated", updated)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(Authentication auth) {
        UUID orgId = orgId(auth);
        Map<String, Object> stats = new HashMap<>();
        for (VerificationDecision decision : VerificationDecision.values()) {
            stats.put(decision.name().toLowerCase(),
                    verificationRepository.countByOrganizationIdAndDecision(orgId, decision));
        }
        stats.put("openScreeningHits", sanctionsHitRepository.countByOrganizationIdAndStatus(orgId, "OPEN"));
        stats.put("autoDismissedHits", sanctionsHitRepository.countByOrganizationIdAndDismissedByAiTrue(orgId));
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }

    private UUID actorId(Authentication auth) {
        return UUID.fromString((String) auth.getPrincipal());
    }
}
