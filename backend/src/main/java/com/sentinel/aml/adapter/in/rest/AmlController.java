package com.sentinel.aml.adapter.in.rest;

import com.sentinel.aml.adapter.out.persistence.AmlFindingRepository;
import com.sentinel.aml.adapter.out.persistence.SarFilingRepository;
import com.sentinel.aml.application.service.SarService;
import com.sentinel.aml.application.service.TransactionMonitoringService;
import com.sentinel.aml.domain.model.AmlFinding;
import com.sentinel.aml.domain.model.AmlTypology;
import com.sentinel.aml.domain.model.SarFiling;
import com.sentinel.aml.domain.model.SarStatus;
import com.sentinel.common.dto.ApiResponse;
import com.sentinel.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/aml")
@RequiredArgsConstructor
public class AmlController {

    private final TransactionMonitoringService monitoringService;
    private final SarService sarService;
    private final AmlFindingRepository findingRepository;
    private final SarFilingRepository sarRepository;

    /* ---------- Monitoring ---------- */

    @GetMapping("/findings")
    public ResponseEntity<ApiResponse<Page<AmlFinding>>> findings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            Authentication auth) {
        UUID orgId = orgId(auth);
        var pageable = PageRequest.of(page, size);
        Page<AmlFinding> result = status == null
                ? findingRepository.findByOrganizationIdOrderByScoreDesc(orgId, pageable)
                : findingRepository.findByOrganizationIdAndStatusOrderByScoreDesc(orgId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/monitor/{subjectId}")
    public ResponseEntity<ApiResponse<List<AmlFinding>>> monitor(
            @PathVariable String subjectId,
            @RequestParam(defaultValue = "90") int days,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(monitoringService.monitor(orgId(auth), subjectId, days)));
    }

    @PostMapping("/sweep")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sweep(
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(defaultValue = "500") int maxSubjects,
            Authentication auth) {
        List<AmlFinding> findings = monitoringService.sweep(orgId(auth), days, maxSubjects);
        Map<String, Object> result = new HashMap<>();
        result.put("findings", findings.size());
        result.put("subjectsScanned", maxSubjects);
        result.put("topFindings", findings.stream()
                .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                .limit(10)
                .toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PatchMapping("/findings/{id}/status")
    public ResponseEntity<ApiResponse<AmlFinding>> updateFindingStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        AmlFinding finding = findingRepository.findByIdAndOrganizationId(id, orgId(auth))
                .orElseThrow(() -> new ResourceNotFoundException("AmlFinding", id.toString()));
        finding.setStatus(body.getOrDefault("status", "CLOSED_NO_ACTION"));
        return ResponseEntity.ok(ApiResponse.success(findingRepository.save(finding)));
    }

    /** The typology library, with live counts against each pattern. */
    @GetMapping("/typologies")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> typologies(Authentication auth) {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : findingRepository.countByTypology(orgId(auth))) {
            counts.put(((AmlTypology) row[0]).name(), ((Number) row[1]).longValue());
        }
        List<Map<String, Object>> library = new ArrayList<>();
        for (AmlTypology typology : AmlTypology.values()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("typology", typology.name());
            entry.put("label", typology.getLabel());
            entry.put("description", typology.getDescription());
            entry.put("reference", typology.getReference());
            entry.put("findings", counts.getOrDefault(typology.name(), 0L));
            library.add(entry);
        }
        return ResponseEntity.ok(ApiResponse.success(library));
    }

    /* ---------- Suspicious activity reports ---------- */

    @PostMapping("/sar/draft")
    public ResponseEntity<ApiResponse<SarFiling>> draftSar(
            @RequestBody Map<String, Object> body, Authentication auth) {
        String subjectId = String.valueOf(body.get("subjectId"));
        String subjectName = body.get("subjectName") == null ? null : String.valueOf(body.get("subjectName"));
        String jurisdiction = body.get("jurisdiction") == null ? "FINCEN" : String.valueOf(body.get("jurisdiction"));

        @SuppressWarnings("unchecked")
        List<String> rawIds = (List<String>) body.getOrDefault("findingIds", List.of());
        List<UUID> findingIds = rawIds.stream().map(UUID::fromString).toList();

        return ResponseEntity.ok(ApiResponse.success(
                sarService.draft(orgId(auth), subjectId, subjectName, jurisdiction, findingIds)));
    }

    @GetMapping("/sar")
    public ResponseEntity<ApiResponse<Page<SarFiling>>> listSars(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) SarStatus status,
            Authentication auth) {
        UUID orgId = orgId(auth);
        var pageable = PageRequest.of(page, size);
        Page<SarFiling> result = status == null
                ? sarRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId, pageable)
                : sarRepository.findByOrganizationIdAndStatusOrderByDeadlineAtAsc(orgId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/sar/{id}")
    public ResponseEntity<ApiResponse<SarFiling>> getSar(@PathVariable UUID id, Authentication auth) {
        SarFiling filing = sarRepository.findByIdAndOrganizationId(id, orgId(auth))
                .orElseThrow(() -> new ResourceNotFoundException("SarFiling", id.toString()));
        return ResponseEntity.ok(ApiResponse.success(filing));
    }

    @PutMapping("/sar/{id}/narrative")
    public ResponseEntity<ApiResponse<SarFiling>> editNarrative(
            @PathVariable UUID id, @RequestBody Map<String, String> body, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                sarService.updateNarrative(orgId(auth), id, body.get("narrative"))));
    }

    @PostMapping("/sar/{id}/approve")
    public ResponseEntity<ApiResponse<SarFiling>> approve(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(sarService.approve(orgId(auth), id, actorId(auth))));
    }

    @PostMapping("/sar/{id}/file")
    public ResponseEntity<ApiResponse<SarFiling>> file(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(sarService.file(orgId(auth), id)));
    }

    @PostMapping("/sar/{id}/reject")
    public ResponseEntity<ApiResponse<SarFiling>> reject(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(sarService.reject(orgId(auth), id, actorId(auth))));
    }

    @GetMapping("/sar/overdue")
    public ResponseEntity<ApiResponse<List<SarFiling>>> overdue(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(sarService.overdue(orgId(auth))));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(Authentication auth) {
        UUID orgId = orgId(auth);
        Map<String, Object> stats = new HashMap<>();
        stats.put("openFindings", findingRepository.countByOrganizationIdAndStatus(orgId, "OPEN"));
        stats.put("escalatedFindings", findingRepository.countByOrganizationIdAndStatus(orgId, "ESCALATED"));
        for (SarStatus status : SarStatus.values()) {
            stats.put("sar" + status.name().charAt(0) + status.name().substring(1).toLowerCase(),
                    sarRepository.countByOrganizationIdAndStatus(orgId, status));
        }
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }

    private UUID actorId(Authentication auth) {
        return UUID.fromString((String) auth.getPrincipal());
    }
}
