package com.sentinel.governance.application.service;

import com.sentinel.common.exception.BusinessException;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.governance.adapter.out.persistence.DriftReportRepository;
import com.sentinel.governance.adapter.out.persistence.FairnessReportRepository;
import com.sentinel.governance.adapter.out.persistence.ModelRegistryRepository;
import com.sentinel.governance.domain.model.DriftReport;
import com.sentinel.governance.domain.model.FairnessReport;
import com.sentinel.governance.domain.model.ModelRegistryEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The model inventory, and the documentation that comes with it.
 *
 * <p>Model documentation is written once, goes stale immediately, and is
 * rewritten in a panic before an examination. Generating it from the live
 * registry — including the current drift and fairness results — means the
 * document is never older than the data it describes.
 *
 * <p>The structure follows what SR 11-7 and the EU AI Act both want: purpose,
 * design, data, performance, limitations, monitoring, and governance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelRegistryService {

    private final ModelRegistryRepository registryRepository;
    private final DriftReportRepository driftRepository;
    private final FairnessReportRepository fairnessRepository;

    @Transactional
    public ModelRegistryEntry register(UUID orgId, ModelRegistryEntry incoming) {
        ModelRegistryEntry entry = registryRepository
                .findByOrganizationIdAndModelNameAndModelVersion(
                        orgId, incoming.getModelName(), incoming.getModelVersion())
                .orElseGet(() -> ModelRegistryEntry.builder()
                        .organizationId(orgId)
                        .modelName(incoming.getModelName())
                        .modelVersion(incoming.getModelVersion())
                        .build());

        entry.setModelType(incoming.getModelType());
        entry.setStatus(incoming.getStatus() == null ? "DEVELOPMENT" : incoming.getStatus());
        entry.setRiskTier(incoming.getRiskTier() == null ? "HIGH" : incoming.getRiskTier());
        entry.setPurpose(incoming.getPurpose());
        entry.setOwnerName(incoming.getOwnerName());
        entry.setTrainingData(incoming.getTrainingData());
        entry.setMetrics(incoming.getMetrics());
        entry.setLimitations(incoming.getLimitations());
        entry.setInputFeatures(incoming.getInputFeatures());
        entry.setNextReviewAt(Instant.now().plus(365, ChronoUnit.DAYS));

        ModelRegistryEntry saved = registryRepository.save(entry);
        saved.setDocumentation(generateDocumentation(orgId, saved));
        return registryRepository.save(saved);
    }

    /**
     * Promotion to production requires a named approver. A model that reached
     * production with nobody's name against it is the finding, not the risk.
     */
    @Transactional
    public ModelRegistryEntry approve(UUID orgId, UUID modelId, UUID approverId, String validatedBy) {
        ModelRegistryEntry entry = registryRepository.findByIdAndOrganizationId(modelId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("ModelRegistryEntry", modelId.toString()));

        if (entry.getPurpose() == null || entry.getPurpose().isBlank()) {
            throw new BusinessException("INCOMPLETE_MODEL_RECORD",
                    "A model cannot be approved without a documented purpose");
        }
        if (entry.getLimitations() == null || entry.getLimitations().isEmpty()) {
            throw new BusinessException("INCOMPLETE_MODEL_RECORD",
                    "A model cannot be approved without documented limitations");
        }

        entry.setStatus("APPROVED");
        entry.setApprovedBy(approverId);
        entry.setValidatedBy(validatedBy);
        entry.setApprovedAt(Instant.now());
        entry.setDocumentation(generateDocumentation(orgId, entry));

        log.info("Model {} v{} approved by {}", entry.getModelName(), entry.getModelVersion(), approverId);
        return registryRepository.save(entry);
    }

    @Transactional
    public ModelRegistryEntry setStatus(UUID orgId, UUID modelId, String status) {
        ModelRegistryEntry entry = registryRepository.findByIdAndOrganizationId(modelId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("ModelRegistryEntry", modelId.toString()));
        if ("PRODUCTION".equals(status) && entry.getApprovedAt() == null) {
            throw new BusinessException("NOT_APPROVED",
                    "A model must be approved before it goes to production");
        }
        entry.setStatus(status);
        return registryRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<ModelRegistryEntry> list(UUID orgId) {
        return registryRepository.findByOrganizationIdOrderByModelNameAscModelVersionDesc(orgId);
    }

    @Transactional(readOnly = true)
    public String documentationFor(UUID orgId, UUID modelId) {
        ModelRegistryEntry entry = registryRepository.findByIdAndOrganizationId(modelId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("ModelRegistryEntry", modelId.toString()));
        return generateDocumentation(orgId, entry);
    }

    private String generateDocumentation(UUID orgId, ModelRegistryEntry entry) {
        StringBuilder doc = new StringBuilder();

        doc.append("# Model documentation: ").append(entry.getModelName())
                .append(" v").append(entry.getModelVersion()).append("\n\n");
        doc.append("Generated ").append(Instant.now()).append(" from the live model registry.\n\n");

        doc.append("## 1. Purpose and scope\n");
        doc.append(entry.getPurpose() == null ? "Not documented." : entry.getPurpose()).append("\n\n");
        doc.append("- Model type: ").append(entry.getModelType()).append('\n');
        doc.append("- Lifecycle status: ").append(entry.getStatus()).append('\n');
        doc.append("- Risk tier (EU AI Act): ").append(entry.getRiskTier()).append('\n');
        doc.append("- Owner: ").append(entry.getOwnerName()).append("\n\n");

        doc.append("## 2. Inputs\n");
        if (entry.getInputFeatures() == null || entry.getInputFeatures().isEmpty()) {
            doc.append("Input features are not documented.\n\n");
        } else {
            entry.getInputFeatures().forEach(f -> doc.append("- ").append(f).append('\n'));
            doc.append('\n');
        }

        doc.append("## 3. Development data\n");
        doc.append(entry.getTrainingData() == null
                ? "Training data is not documented.\n\n"
                : entry.getTrainingData() + "\n\n");

        doc.append("## 4. Performance\n");
        if (entry.getMetrics() == null || entry.getMetrics().isEmpty()) {
            doc.append("No performance metrics recorded.\n\n");
        } else {
            for (Map.Entry<String, Object> metric : entry.getMetrics().entrySet()) {
                doc.append("- ").append(metric.getKey()).append(": ").append(metric.getValue()).append('\n');
            }
            doc.append('\n');
        }

        doc.append("## 5. Known limitations\n");
        if (entry.getLimitations() == null || entry.getLimitations().isEmpty()) {
            doc.append("Limitations are not documented. This must be completed before approval.\n\n");
        } else {
            entry.getLimitations().forEach(l -> doc.append("- ").append(l).append('\n'));
            doc.append('\n');
        }

        doc.append("## 6. Ongoing monitoring\n");
        List<DriftReport> drift = driftRepository
                .findByOrganizationIdOrderByCreatedAtDesc(orgId, PageRequest.of(0, 3)).getContent();
        if (drift.isEmpty()) {
            doc.append("No drift measurements recorded.\n");
        } else {
            doc.append("Population stability (most recent measurements):\n");
            drift.forEach(d -> doc.append("- ").append(d.getCreatedAt()).append(": PSI ")
                    .append(d.getPsi()).append(" (").append(d.getStatus()).append(")\n"));
        }
        doc.append('\n');

        doc.append("## 7. Fairness and disparate impact\n");
        List<FairnessReport> fairness = fairnessRepository
                .findByOrganizationIdOrderByCreatedAtDesc(orgId, PageRequest.of(0, 3)).getContent();
        if (fairness.isEmpty()) {
            doc.append("No disparate-impact testing recorded.\n");
        } else {
            fairness.forEach(f -> doc.append("- ").append(f.getAttribute()).append(": ratio ")
                    .append(f.getDisparityRatio()).append(" — ").append(f.getVerdict()).append('\n'));
        }
        doc.append('\n');

        doc.append("## 8. Governance\n");
        doc.append("- Validated by: ").append(entry.getValidatedBy() == null ? "pending" : entry.getValidatedBy()).append('\n');
        doc.append("- Approved: ").append(entry.getApprovedAt() == null ? "not approved" : entry.getApprovedAt()).append('\n');
        doc.append("- Next review due: ").append(entry.getNextReviewAt()).append('\n');
        doc.append("- Every decision this model produces is recorded with its inputs and is replayable.\n");

        return doc.toString();
    }
}
