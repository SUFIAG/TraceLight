package com.sentinel.graph.application.service;

import com.sentinel.graph.application.dto.GraphView;
import com.sentinel.graph.domain.model.EntityLink;
import com.sentinel.mule.application.service.BeneficiaryRiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Builds the investigator-facing view of the graph around one entity.
 *
 * <p>Node risk is read from the beneficiary register where it exists, and
 * otherwise inferred from how tightly the node sits in the neighbourhood.
 * Degree is a real signal here: in a mule network the controller is the node
 * everything touches.
 */
@Service
@RequiredArgsConstructor
public class LinkAnalysisService {

    private final EntityResolutionService entityResolutionService;
    private final BeneficiaryRiskService beneficiaryRiskService;

    @Transactional(readOnly = true)
    public GraphView neighbourhood(UUID orgId, String entityId, int depth) {
        List<EntityLink> links = entityResolutionService.expand(orgId, entityId, depth);

        Map<String, Integer> degree = new HashMap<>();
        List<GraphView.Edge> edges = new ArrayList<>();

        for (EntityLink link : links) {
            degree.merge(link.getSourceId(), 1, Integer::sum);
            degree.merge(link.getTargetId(), 1, Integer::sum);
            edges.add(GraphView.Edge.builder()
                    .source(link.getSourceId())
                    .target(link.getTargetId())
                    .type(link.getLinkType().name())
                    .label(link.getLinkType().getLabel())
                    .strength(link.getStrength())
                    .via(link.getViaValue())
                    .observations(link.getObservationCount())
                    .build());
        }

        degree.putIfAbsent(entityId, 0);

        List<GraphView.Node> nodes = degree.entrySet().stream()
                .map(entry -> GraphView.Node.builder()
                        .id(entry.getKey())
                        .type("CUSTOMER")
                        .label(entry.getKey())
                        .risk(riskOf(orgId, entry.getKey(), entry.getValue()))
                        .root(entry.getKey().equals(entityId))
                        .degree(entry.getValue())
                        .build())
                .sorted(Comparator.comparingInt(GraphView.Node::getDegree).reversed())
                .toList();

        return GraphView.builder()
                .rootId(entityId)
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    private int riskOf(UUID orgId, String entityId, int degree) {
        int known = beneficiaryRiskService.riskOf(orgId, entityId);
        if (known > 0) return known;
        // Nothing recorded against this entity, so fall back to connectivity.
        return Math.min(60, degree * 6);
    }
}
