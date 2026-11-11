package com.sentinel.strategy.adapter.in.rest;

import com.sentinel.security.application.service.StepUpRequired;
import com.sentinel.common.dto.ApiResponse;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.strategy.adapter.out.persistence.RuleBacktestRepository;
import com.sentinel.strategy.adapter.out.persistence.RuleDefinitionRepository;
import com.sentinel.strategy.application.service.*;
import com.sentinel.strategy.domain.model.RuleBacktest;
import com.sentinel.strategy.domain.model.RuleDefinition;
import com.sentinel.strategy.domain.model.RuleStatus;
import com.sentinel.transaction.adapter.out.persistence.TransactionRepository;
import com.sentinel.transaction.domain.model.Transaction;
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

/**
 * The strategy studio: author rules in English, backtest them, run them in
 * shadow, promote the winner, and tune the cut-off.
 */
@RestController
@RequestMapping("/api/v1/strategy")
@RequiredArgsConstructor
public class StrategyController {

    private final RuleLifecycleService lifecycleService;
    private final NaturalLanguageRuleService naturalLanguageRuleService;
    private final RuleBacktestService backtestService;
    private final RuleDiscoveryService discoveryService;
    private final ThresholdTuningService tuningService;
    private final RetroAnalysisService retroAnalysisService;
    private final RuleDefinitionRepository ruleRepository;
    private final RuleBacktestRepository backtestRepository;
    private final TransactionRepository transactionRepository;

    /* ---------- Authoring ---------- */

    /** Parses a sentence without saving, so the author can check the readback first. */
    @PostMapping("/rules/parse")
    public ResponseEntity<ApiResponse<Map<String, Object>>> parse(@RequestBody Map<String, String> body) {
        NaturalLanguageRuleService.Parsed parsed = naturalLanguageRuleService.parse(body.get("sentence"));
        Map<String, Object> response = new HashMap<>();
        response.put("definition", parsed.definition());
        response.put("explanation", parsed.explanation());
        response.put("llmAssisted", parsed.llmAssisted());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/rules")
    public ResponseEntity<ApiResponse<RuleDefinition>> create(
            @RequestBody Map<String, Object> body, Authentication auth) {
        UUID orgId = orgId(auth);
        String name = String.valueOf(body.get("name"));
        int riskPoints = body.get("riskPoints") == null ? 60 : ((Number) body.get("riskPoints")).intValue();

        if (body.get("sentence") != null) {
            return ResponseEntity.ok(ApiResponse.success(lifecycleService.createFromSentence(
                    orgId, name, String.valueOf(body.get("sentence")), riskPoints, actorId(auth))));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> definition = (Map<String, Object>) body.get("definition");
        String description = body.get("description") == null ? null : String.valueOf(body.get("description"));
        return ResponseEntity.ok(ApiResponse.success(lifecycleService.createFromDefinition(
                orgId, name, description, definition, riskPoints, actorId(auth))));
    }

    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<Page<RuleDefinition>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                ruleRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId(auth), PageRequest.of(page, size))));
    }

    @GetMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<RuleDefinition>> get(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(lifecycleService.get(orgId(auth), id)));
    }

    /** Promotion changes what the platform does to customers, so it is restricted. */
    @PostMapping("/rules/{id}/promote")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    @StepUpRequired
    public ResponseEntity<ApiResponse<RuleDefinition>> promote(
            @PathVariable UUID id, @RequestBody Map<String, Object> body, Authentication auth) {
        RuleStatus target = RuleStatus.valueOf(String.valueOf(body.get("status")));
        int traffic = body.get("trafficPercent") == null ? 10 : ((Number) body.get("trafficPercent")).intValue();
        return ResponseEntity.ok(ApiResponse.success(
                lifecycleService.promote(orgId(auth), id, target, traffic, actorId(auth))));
    }

    /* ---------- Simulation ---------- */

    @PostMapping("/rules/{id}/backtest")
    public ResponseEntity<ApiResponse<RuleBacktest>> backtest(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(defaultValue = "2000") int sample,
            Authentication auth) {
        UUID orgId = orgId(auth);
        RuleDefinition rule = lifecycleService.get(orgId, id);
        return ResponseEntity.ok(ApiResponse.success(backtestService.run(orgId, rule, days, sample)));
    }

    /** Backtests a definition that has not been saved — the "what if" box. */
    @PostMapping("/simulate")
    public ResponseEntity<ApiResponse<RuleBacktest>> simulate(
            @RequestBody Map<String, Object> body, Authentication auth) {
        UUID orgId = orgId(auth);
        int days = body.get("days") == null ? 90 : ((Number) body.get("days")).intValue();
        int sample = body.get("sample") == null ? 2000 : ((Number) body.get("sample")).intValue();
        int riskPoints = body.get("riskPoints") == null ? 60 : ((Number) body.get("riskPoints")).intValue();

        Map<String, Object> definition;
        String name;
        if (body.get("sentence") != null) {
            NaturalLanguageRuleService.Parsed parsed =
                    naturalLanguageRuleService.parse(String.valueOf(body.get("sentence")));
            definition = parsed.definition();
            name = "Simulation: " + body.get("sentence");
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = (Map<String, Object>) body.get("definition");
            definition = raw;
            name = body.get("name") == null ? "Simulation" : String.valueOf(body.get("name"));
        }

        RuleDefinition transient_ = RuleDefinition.builder()
                .organizationId(orgId)
                .name(name)
                .definition(definition)
                .riskPoints(riskPoints)
                .status(RuleStatus.DRAFT)
                .version(0)
                .build();

        return ResponseEntity.ok(ApiResponse.success(backtestService.run(orgId, transient_, days, sample)));
    }

    @GetMapping("/backtests")
    public ResponseEntity<ApiResponse<Page<RuleBacktest>>> backtests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                backtestRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId(auth), PageRequest.of(page, size))));
    }

    /** Evaluates every live rule against one real transaction, shadow rules included. */
    @GetMapping("/evaluate/{transactionId}")
    public ResponseEntity<ApiResponse<List<RuleLifecycleService.Evaluation>>> evaluate(
            @PathVariable UUID transactionId, Authentication auth) {
        UUID orgId = orgId(auth);
        Transaction transaction = transactionRepository.findById(transactionId)
                .filter(t -> t.getOrganizationId().equals(orgId))
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId.toString()));
        return ResponseEntity.ok(ApiResponse.success(lifecycleService.evaluateAll(orgId, transaction)));
    }

    /* ---------- Discovery and tuning ---------- */

    @PostMapping("/discover")
    public ResponseEntity<ApiResponse<List<RuleDiscoveryService.Candidate>>> discover(
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(defaultValue = "2000") int sample,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(discoveryService.discover(orgId(auth), days, sample)));
    }

    @GetMapping("/tuning")
    public ResponseEntity<ApiResponse<ThresholdTuningService.TuningResult>> tuning(
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(required = false) Double frictionWeight,
            @RequestParam(required = false) Double reviewCost,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                tuningService.tune(orgId(auth), days, frictionWeight, reviewCost)));
    }

    @PostMapping("/retro")
    public ResponseEntity<ApiResponse<RetroAnalysisService.RetroResult>> retro(
            @RequestParam(defaultValue = "365") int days,
            @RequestParam(defaultValue = "5000") int sample,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(retroAnalysisService.analyse(orgId(auth), days, sample)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(Authentication auth) {
        UUID orgId = orgId(auth);
        Map<String, Object> stats = new HashMap<>();
        for (RuleStatus status : RuleStatus.values()) {
            stats.put(status.name().toLowerCase(), ruleRepository.countByOrganizationIdAndStatus(orgId, status));
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
