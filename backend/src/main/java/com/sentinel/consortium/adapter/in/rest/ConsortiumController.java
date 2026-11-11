package com.sentinel.consortium.adapter.in.rest;

import com.sentinel.common.dto.ApiResponse;
import com.sentinel.consortium.adapter.out.persistence.ConsortiumContributionRepository;
import com.sentinel.consortium.adapter.out.persistence.ConsortiumSignalRepository;
import com.sentinel.consortium.application.service.ConsortiumService;
import com.sentinel.consortium.domain.model.ConsortiumSignal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/consortium")
@RequiredArgsConstructor
public class ConsortiumController {

    private static final int MIN_CONTRIBUTORS = 2;

    private final ConsortiumService consortiumService;
    private final ConsortiumSignalRepository signalRepository;
    private final ConsortiumContributionRepository contributionRepository;

    /** Reports a bad identifier. It is hashed before it is stored. */
    @PostMapping("/contribute")
    public ResponseEntity<ApiResponse<Map<String, Object>>> contribute(
            @RequestBody Map<String, Object> body, Authentication auth) {
        ConsortiumSignal signal = consortiumService.contribute(
                orgId(auth),
                String.valueOf(body.get("identifier")),
                String.valueOf(body.getOrDefault("identifierType", "ACCOUNT")),
                String.valueOf(body.getOrDefault("signalType", "MULE_ACCOUNT")),
                body.get("severity") == null ? 80 : ((Number) body.get("severity")).intValue());

        // The raw identifier is never echoed back, not even to the contributor.
        Map<String, Object> response = new HashMap<>();
        response.put("signalType", signal.getSignalType());
        response.put("severity", signal.getSeverity());
        response.put("contributorCount", signal.getContributorCount());
        response.put("shared", signal.getContributorCount() >= MIN_CONTRIBUTORS);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** Checks an identifier against the network. */
    @PostMapping("/query")
    public ResponseEntity<ApiResponse<Map<String, Object>>> query(@RequestBody Map<String, String> body) {
        String identifier = body.get("identifier");
        String type = body.getOrDefault("identifierType", "ACCOUNT");
        List<ConsortiumService.Match> matches = consortiumService.query(identifier, type);

        Map<String, Object> response = new HashMap<>();
        response.put("matches", matches);
        response.put("risk", matches.stream().mapToInt(ConsortiumService.Match::severity).max().orElse(0));
        response.put("known", !matches.isEmpty());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/signals")
    public ResponseEntity<ApiResponse<Page<ConsortiumSignal>>> signals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                signalRepository.findByContributorCountGreaterThanEqualOrderByLastSeenDesc(
                        MIN_CONTRIBUTORS, PageRequest.of(page, size))));
    }

    /** Network-wide counts by signal type — the benchmark view. */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> summary() {
        List<Map<String, Object>> summary = new ArrayList<>();
        for (Object[] row : signalRepository.summaryByType(MIN_CONTRIBUTORS)) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("signalType", row[0]);
            entry.put("count", ((Number) row[1]).longValue());
            entry.put("averageSeverity", Math.round(((Number) row[2]).doubleValue()));
            summary.add(entry);
        }
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(Authentication auth) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("sharedSignals", signalRepository.countByContributorCountGreaterThanEqual(MIN_CONTRIBUTORS));
        stats.put("totalSignals", signalRepository.count());
        stats.put("yourContributions", contributionRepository.countByOrganizationId(orgId(auth)));
        stats.put("minContributorsToShare", MIN_CONTRIBUTORS);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /** Withdraws everything this organization has ever contributed. */
    @DeleteMapping("/contributions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> withdraw(Authentication auth) {
        consortiumService.withdraw(orgId(auth));
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "withdrawn")));
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }
}
