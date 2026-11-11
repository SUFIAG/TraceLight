package com.sentinel.scam.adapter.in.rest;

import com.sentinel.common.dto.ApiResponse;
import com.sentinel.scam.adapter.out.persistence.ScamSignalRepository;
import com.sentinel.scam.adapter.out.persistence.TrustedContactRepository;
import com.sentinel.scam.application.dto.PaymentHoldResponse;
import com.sentinel.scam.application.dto.ScamAssessmentRequest;
import com.sentinel.scam.application.dto.ScamAssessmentResponse;
import com.sentinel.scam.application.dto.TrustedContactRequest;
import com.sentinel.scam.application.service.CoolingOffService;
import com.sentinel.scam.application.service.ScamDetectionService;
import com.sentinel.scam.application.service.VulnerableCustomerService;
import com.sentinel.scam.domain.model.ScamSignal;
import com.sentinel.scam.domain.model.ScamTypology;
import com.sentinel.scam.domain.model.TrustedContact;
import com.sentinel.scam.domain.model.VulnerableCustomer;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/scam")
@RequiredArgsConstructor
public class ScamController {

    private final ScamDetectionService scamDetectionService;
    private final CoolingOffService coolingOffService;
    private final VulnerableCustomerService vulnerableCustomerService;
    private final ScamSignalRepository scamSignalRepository;
    private final TrustedContactRepository trustedContactRepository;

    /** Called inline by the payment flow, before the customer's final confirmation. */
    @PostMapping("/assess")
    public ResponseEntity<ApiResponse<ScamAssessmentResponse>> assess(
            @RequestBody @Valid ScamAssessmentRequest request, Authentication auth) {
        UUID orgId = orgId(auth);
        ScamAssessmentResponse response = scamDetectionService.assess(orgId, request);
        vulnerableCustomerService.reassess(orgId, request.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/signals")
    public ResponseEntity<ApiResponse<Page<ScamSignal>>> signals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ScamTypology typology,
            Authentication auth) {
        UUID orgId = orgId(auth);
        var pageable = PageRequest.of(page, size);
        Page<ScamSignal> result = typology == null
                ? scamSignalRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId, pageable)
                : scamSignalRepository.findByOrganizationIdAndTypologyOrderByCreatedAtDesc(orgId, typology, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** Scam volume and exposure by family — the view a scam lead reports upward. */
    @GetMapping("/typologies")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> typologies(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "30") int minScore,
            Authentication auth) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> rows = scamSignalRepository.breakdownByTypology(orgId(auth), since, minScore);
        List<Map<String, Object>> breakdown = new ArrayList<>();
        for (Object[] row : rows) {
            ScamTypology typology = (ScamTypology) row[0];
            Map<String, Object> entry = new HashMap<>();
            entry.put("typology", typology.name());
            entry.put("label", typology.getLabel());
            entry.put("description", typology.getDescription());
            entry.put("count", ((Number) row[1]).longValue());
            entry.put("exposure", row[2] == null ? BigDecimal.ZERO : row[2]);
            breakdown.add(entry);
        }
        return ResponseEntity.ok(ApiResponse.success(breakdown));
    }

    /* ---------- Cooling-off holds ---------- */

    @GetMapping("/holds")
    public ResponseEntity<ApiResponse<List<PaymentHoldResponse>>> holds(Authentication auth) {
        List<PaymentHoldResponse> holds = coolingOffService.listActive(orgId(auth)).stream()
                .map(PaymentHoldResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(holds));
    }

    @PostMapping("/holds/{id}/release")
    public ResponseEntity<ApiResponse<PaymentHoldResponse>> release(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                PaymentHoldResponse.from(coolingOffService.release(orgId(auth), id, actorId(auth)))));
    }

    @PostMapping("/holds/{id}/cancel")
    public ResponseEntity<ApiResponse<PaymentHoldResponse>> cancel(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean byCustomer,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                PaymentHoldResponse.from(coolingOffService.cancel(orgId(auth), id, actorId(auth), byCustomer))));
    }

    /* ---------- Trusted contacts ---------- */

    @GetMapping("/trusted-contacts/{userId}")
    public ResponseEntity<ApiResponse<List<TrustedContact>>> trustedContacts(
            @PathVariable String userId, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                trustedContactRepository.findByOrganizationIdAndUserIdAndActiveTrue(orgId(auth), userId)));
    }

    @PostMapping("/trusted-contacts")
    public ResponseEntity<ApiResponse<TrustedContact>> addTrustedContact(
            @RequestBody @Valid TrustedContactRequest request, Authentication auth) {
        TrustedContact contact = trustedContactRepository.save(TrustedContact.builder()
                .organizationId(orgId(auth))
                .userId(request.getUserId())
                .contactName(request.getContactName())
                .contactChannel(request.getContactChannel())
                .contactAddress(mask(request.getContactAddress()))
                .relationship(request.getRelationship())
                .notifyAboveAmount(request.getNotifyAboveAmount())
                .active(true)
                .build());
        return ResponseEntity.ok(ApiResponse.success(contact));
    }

    /* ---------- Adapted protection ---------- */

    @GetMapping("/vulnerable")
    public ResponseEntity<ApiResponse<List<VulnerableCustomer>>> vulnerable(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(vulnerableCustomerService.list(orgId(auth))));
    }

    @PostMapping("/vulnerable")
    public ResponseEntity<ApiResponse<VulnerableCustomer>> declareVulnerable(
            @RequestBody Map<String, Object> body, Authentication auth) {
        String userId = String.valueOf(body.get("userId"));
        String basis = body.get("basis") == null ? "DECLARED" : String.valueOf(body.get("basis"));
        int level = body.get("protectionLevel") == null ? 2 : ((Number) body.get("protectionLevel")).intValue();
        @SuppressWarnings("unchecked")
        List<String> evidence = (List<String>) body.getOrDefault("evidence", List.of());
        return ResponseEntity.ok(ApiResponse.success(
                vulnerableCustomerService.declare(orgId(auth), userId, basis, level, evidence)));
    }

    @DeleteMapping("/vulnerable/{userId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> clearVulnerable(
            @PathVariable String userId, Authentication auth) {
        vulnerableCustomerService.clear(orgId(auth), userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "cleared", "userId", userId)));
    }

    /**
     * Trusted-contact destinations are stored masked. Proving the notification
     * was due does not require holding a third party's phone number in full.
     */
    private String mask(String address) {
        if (address == null || address.length() <= 4) return "****";
        return "*".repeat(address.length() - 4) + address.substring(address.length() - 4);
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }

    private UUID actorId(Authentication auth) {
        return UUID.fromString((String) auth.getPrincipal());
    }
}
