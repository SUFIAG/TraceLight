package com.sentinel.mule.adapter.in.rest;

import com.sentinel.common.dto.ApiResponse;
import com.sentinel.mule.adapter.out.persistence.BeneficiaryRiskRepository;
import com.sentinel.mule.application.dto.BeneficiaryRiskResponse;
import com.sentinel.mule.application.service.BeneficiaryRiskService;
import com.sentinel.mule.domain.model.MuleBand;
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
@RequestMapping("/api/v1/beneficiaries")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryRiskService beneficiaryRiskService;
    private final BeneficiaryRiskRepository beneficiaryRiskRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<BeneficiaryRiskResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        UUID orgId = orgId(auth);
        Page<BeneficiaryRiskResponse> result = beneficiaryRiskRepository
                .findByOrganizationIdOrderByScoreDesc(orgId, PageRequest.of(page, size))
                .map(BeneficiaryRiskResponse::from);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{beneficiaryId}")
    public ResponseEntity<ApiResponse<BeneficiaryRiskResponse>> get(
            @PathVariable String beneficiaryId, Authentication auth) {
        UUID orgId = orgId(auth);
        BeneficiaryRiskResponse response = beneficiaryRiskService.find(orgId, beneficiaryId)
                .map(BeneficiaryRiskResponse::from)
                .orElseGet(() -> BeneficiaryRiskResponse.from(beneficiaryRiskService.score(orgId, beneficiaryId)));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** Recomputes a single beneficiary from the last 30 days of credits. */
    @PostMapping("/{beneficiaryId}/score")
    public ResponseEntity<ApiResponse<BeneficiaryRiskResponse>> score(
            @PathVariable String beneficiaryId, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                BeneficiaryRiskResponse.from(beneficiaryRiskService.score(orgId(auth), beneficiaryId))));
    }

    /** Rescores every beneficiary with meaningful inbound traffic. */
    @PostMapping("/sweep")
    public ResponseEntity<ApiResponse<List<BeneficiaryRiskResponse>>> sweep(
            @RequestParam(defaultValue = "200") int limit, Authentication auth) {
        List<BeneficiaryRiskResponse> scored = beneficiaryRiskService.sweep(orgId(auth), limit).stream()
                .map(BeneficiaryRiskResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(scored));
    }

    @PostMapping("/{beneficiaryId}/confirm")
    public ResponseEntity<ApiResponse<BeneficiaryRiskResponse>> confirm(
            @PathVariable String beneficiaryId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        String source = body != null && body.get("source") != null ? body.get("source") : "ANALYST";
        return ResponseEntity.ok(ApiResponse.success(
                BeneficiaryRiskResponse.from(beneficiaryRiskService.confirm(orgId(auth), beneficiaryId, source))));
    }

    /** Everyone who paid this account — the victim list for outreach and fund recall. */
    @GetMapping("/{beneficiaryId}/payers")
    public ResponseEntity<ApiResponse<List<String>>> payers(
            @PathVariable String beneficiaryId, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(beneficiaryRiskService.payersOf(orgId(auth), beneficiaryId)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Long>>> stats(Authentication auth) {
        UUID orgId = orgId(auth);
        Map<String, Long> stats = Map.of(
                "clean", beneficiaryRiskRepository.countByOrganizationIdAndBand(orgId, MuleBand.CLEAN),
                "watch", beneficiaryRiskRepository.countByOrganizationIdAndBand(orgId, MuleBand.WATCH),
                "suspected", beneficiaryRiskRepository.countByOrganizationIdAndBand(orgId, MuleBand.SUSPECTED),
                "confirmed", beneficiaryRiskRepository.countByOrganizationIdAndBand(orgId, MuleBand.CONFIRMED)
        );
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }
}
