package com.sentinel.disputes.adapter.in.rest;

import com.sentinel.common.dto.ApiResponse;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.disputes.adapter.out.persistence.DisputeRepository;
import com.sentinel.disputes.application.service.DisputeService;
import com.sentinel.disputes.domain.model.Dispute;
import com.sentinel.disputes.domain.model.DisputeStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;
    private final DisputeRepository disputeRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<Dispute>> intake(
            @RequestBody Map<String, String> body, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(disputeService.intake(
                orgId(auth),
                UUID.fromString(body.get("transactionId")),
                body.getOrDefault("network", "VISA"),
                body.getOrDefault("reasonCode", "10.4"),
                body.get("reasonDescription"),
                body.get("externalReference"))));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Dispute>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) DisputeStatus status,
            Authentication auth) {
        UUID orgId = orgId(auth);
        var pageable = PageRequest.of(page, size);
        Page<Dispute> result = status == null
                ? disputeRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId, pageable)
                : disputeRepository.findByOrganizationIdAndStatusOrderByRespondByAsc(orgId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Dispute>> get(@PathVariable UUID id, Authentication auth) {
        Dispute dispute = disputeRepository.findByIdAndOrganizationId(id, orgId(auth))
                .orElseThrow(() -> new ResourceNotFoundException("Dispute", id.toString()));
        return ResponseEntity.ok(ApiResponse.success(dispute));
    }

    @PostMapping("/{id}/draft")
    public ResponseEntity<ApiResponse<Dispute>> draft(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(disputeService.draftRepresentment(orgId(auth), id)));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<Dispute>> submit(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(disputeService.submit(orgId(auth), id)));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<Dispute>> resolve(
            @PathVariable UUID id, @RequestBody Map<String, Object> body, Authentication auth) {
        DisputeStatus outcome = DisputeStatus.valueOf(String.valueOf(body.getOrDefault("outcome", "LOST")));
        BigDecimal recovered = body.get("recoveredAmount") == null ? null
                : new BigDecimal(String.valueOf(body.get("recoveredAmount")));
        return ResponseEntity.ok(ApiResponse.success(disputeService.resolve(orgId(auth), id, outcome, recovered)));
    }

    @GetMapping("/overdue")
    public ResponseEntity<ApiResponse<List<Dispute>>> overdue(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(disputeService.overdue(orgId(auth))));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(
            @RequestParam(defaultValue = "90") int days, Authentication auth) {
        UUID orgId = orgId(auth);
        Map<String, Object> stats = new HashMap<>();
        for (DisputeStatus status : DisputeStatus.values()) {
            stats.put(status.name().toLowerCase(), disputeRepository.countByOrganizationIdAndStatus(orgId, status));
        }
        stats.put("totalRecovered", disputeRepository.totalRecovered(orgId));
        stats.put("totalDisputed", disputeRepository.totalDisputed(orgId,
                Instant.now().minus(days, ChronoUnit.DAYS)));
        long won = disputeRepository.countByOrganizationIdAndStatus(orgId, DisputeStatus.WON);
        long lost = disputeRepository.countByOrganizationIdAndStatus(orgId, DisputeStatus.LOST);
        stats.put("winRate", won + lost == 0 ? null : Math.round((double) won / (won + lost) * 1000) / 10.0);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }
}
