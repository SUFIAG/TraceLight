package com.sentinel.autonomy.adapter.in.rest;

import com.sentinel.security.application.service.StepUpRequired;
import com.sentinel.autonomy.adapter.out.persistence.AgentRunRepository;
import com.sentinel.autonomy.adapter.out.persistence.AttackSimulationRepository;
import com.sentinel.autonomy.adapter.out.persistence.OutreachAttemptRepository;
import com.sentinel.autonomy.application.service.*;
import com.sentinel.autonomy.domain.model.*;
import com.sentinel.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/autonomy")
@RequiredArgsConstructor
public class AutonomyController {

    private final AutonomyPolicyService policyService;
    private final TriageAgentService triageAgent;
    private final OutreachAgentService outreachAgent;
    private final RedTeamAgentService redTeamAgent;
    private final AgentRunRepository agentRunRepository;
    private final OutreachAttemptRepository outreachRepository;
    private final AttackSimulationRepository simulationRepository;

    /* ---------- Policies ---------- */

    @GetMapping("/policies")
    public ResponseEntity<ApiResponse<List<AutonomyPolicy>>> policies(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(policyService.list(orgId(auth))));
    }

    /** Changing what agents may do unaided is an administrator decision. */
    @PutMapping("/policies")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    @StepUpRequired
    public ResponseEntity<ApiResponse<AutonomyPolicy>> savePolicy(
            @RequestBody Map<String, Object> body, Authentication auth) {
        AutonomyPolicy incoming = AutonomyPolicy.builder()
                .actionType(AgentActionType.valueOf(String.valueOf(body.get("actionType"))))
                .level(AutonomyLevel.valueOf(String.valueOf(body.get("level"))))
                .maxAmount(body.get("maxAmount") == null ? null
                        : new BigDecimal(String.valueOf(body.get("maxAmount"))))
                .maxRiskScore(body.get("maxRiskScore") == null ? null
                        : ((Number) body.get("maxRiskScore")).intValue())
                .minConfidence(body.get("minConfidence") == null ? null
                        : ((Number) body.get("minConfidence")).intValue())
                .requiresMakerChecker(Boolean.TRUE.equals(body.get("requiresMakerChecker")))
                .dailyActionCap(body.get("dailyActionCap") == null ? null
                        : ((Number) body.get("dailyActionCap")).intValue())
                .build();
        return ResponseEntity.ok(ApiResponse.success(policyService.save(orgId(auth), incoming, actorId(auth))));
    }

    /** The action catalogue with its conservative defaults, for the policy editor. */
    @GetMapping("/actions")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> actions() {
        List<Map<String, String>> actions = new ArrayList<>();
        for (AgentActionType action : AgentActionType.values()) {
            actions.add(Map.of(
                    "actionType", action.name(),
                    "label", action.getLabel(),
                    "defaultLevel", action.getDefaultLevel().name()));
        }
        return ResponseEntity.ok(ApiResponse.success(actions));
    }

    /* ---------- Triage agent ---------- */

    @PostMapping("/triage/run")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runTriage(
            @RequestParam(defaultValue = "25") int limit, Authentication auth) {
        List<AgentRun> runs = triageAgent.triageQueue(orgId(auth), limit);
        Map<String, Object> result = new HashMap<>();
        result.put("processed", runs.size());
        result.put("resolvedAutonomously", runs.stream().filter(AgentRun::isActionExecuted).count());
        result.put("suppressed", runs.stream().filter(r -> r.getSuppressedReason() != null).count());
        result.put("runs", runs);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/triage/alerts/{alertId}")
    public ResponseEntity<ApiResponse<AgentRun>> triageOne(@PathVariable UUID alertId, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(triageAgent.triage(orgId(auth), alertId)));
    }

    @PostMapping("/runs/{runId}/review")
    public ResponseEntity<ApiResponse<AgentRun>> review(
            @PathVariable UUID runId, @RequestBody Map<String, Boolean> body, Authentication auth) {
        boolean agreed = Boolean.TRUE.equals(body.get("agreed"));
        return ResponseEntity.ok(ApiResponse.success(
                triageAgent.review(orgId(auth), runId, agreed, actorId(auth))));
    }

    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<Page<AgentRun>>> runs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String agent,
            Authentication auth) {
        UUID orgId = orgId(auth);
        var pageable = PageRequest.of(page, size);
        Page<AgentRun> result = agent == null
                ? agentRunRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId, pageable)
                : agentRunRepository.findByOrganizationIdAndAgentNameOrderByCreatedAtDesc(orgId, agent, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /* ---------- Outreach agent ---------- */

    @PostMapping("/outreach/alerts/{alertId}")
    public ResponseEntity<ApiResponse<OutreachAttempt>> contact(
            @PathVariable UUID alertId,
            @RequestParam(defaultValue = "PUSH") String channel,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(outreachAgent.contact(orgId(auth), alertId, channel)));
    }

    @PostMapping("/outreach/{attemptId}/response")
    public ResponseEntity<ApiResponse<OutreachAttempt>> respond(
            @PathVariable UUID attemptId,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        boolean genuine = Boolean.TRUE.equals(body.get("confirmedGenuine"));
        String text = body.get("responseText") == null ? null : String.valueOf(body.get("responseText"));
        return ResponseEntity.ok(ApiResponse.success(
                outreachAgent.recordResponse(orgId(auth), attemptId, genuine, text)));
    }

    @GetMapping("/outreach")
    public ResponseEntity<ApiResponse<Page<OutreachAttempt>>> outreach(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                outreachRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId(auth), PageRequest.of(page, size))));
    }

    /* ---------- Red team agent ---------- */

    @GetMapping("/redteam/scenarios")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> scenarios() {
        List<Map<String, String>> scenarios = redTeamAgent.library().stream()
                .map(s -> Map.of("key", s.key(), "name", s.name(), "description", s.description()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(scenarios));
    }

    @PostMapping("/redteam/run")
    public ResponseEntity<ApiResponse<List<AttackSimulation>>> runRedTeam(
            @RequestParam(required = false) String scenario, Authentication auth) {
        UUID orgId = orgId(auth);
        List<AttackSimulation> results = scenario == null
                ? redTeamAgent.runAll(orgId)
                : List.of(redTeamAgent.run(orgId, scenario));
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @GetMapping("/redteam/results")
    public ResponseEntity<ApiResponse<Page<AttackSimulation>>> redTeamResults(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                simulationRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId(auth), PageRequest.of(page, size))));
    }

    /* ---------- Scorecard ---------- */

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(
            @RequestParam(defaultValue = "30") int days, Authentication auth) {
        UUID orgId = orgId(auth);
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        long total = agentRunRepository.countSince(orgId, since);
        long automated = agentRunRepository.countAutomatedSince(orgId, since);
        long reviewed = agentRunRepository.countReviewed(orgId);
        long agreed = agentRunRepository.countAgreed(orgId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("agentRuns", total);
        stats.put("actionsTaken", automated);
        stats.put("automationRate", total == 0 ? 0 : Math.round((double) automated / total * 1000) / 10.0);
        stats.put("humanReviewed", reviewed);
        stats.put("agreementRate", reviewed == 0 ? null : Math.round((double) agreed / reviewed * 1000) / 10.0);
        stats.put("averageDurationMs", Math.round(agentRunRepository.averageDurationSince(orgId, since)));
        stats.put("outreachConfirmedGenuine",
                outreachRepository.countByOrganizationIdAndOutcome(orgId, "CONFIRMED_GENUINE"));
        stats.put("outreachReportedFraud",
                outreachRepository.countByOrganizationIdAndOutcome(orgId, "REPORTED_FRAUD"));
        stats.put("controlGaps", simulationRepository.findByOrganizationIdAndOutcome(orgId, "FAIL").size());
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }

    private UUID actorId(Authentication auth) {
        return UUID.fromString((String) auth.getPrincipal());
    }
}
