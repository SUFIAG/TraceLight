package com.sentinel.graph.application.service;

import com.sentinel.graph.adapter.out.persistence.EntityLinkRepository;
import com.sentinel.graph.adapter.out.persistence.FraudRingRepository;
import com.sentinel.graph.adapter.out.persistence.GraphAnalyticsRepository;
import com.sentinel.graph.domain.model.EntityLink;
import com.sentinel.graph.domain.model.FraudRing;
import com.sentinel.graph.domain.model.RingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Finds organised groups in the fraud graph.
 *
 * <p>Connected components by union-find over edges above a strength floor,
 * then a density and behaviour score per component. The strength floor is what
 * stops shared-IP noise from merging unrelated clusters: a component held
 * together only by coincidental network addresses never forms.
 *
 * <p>Scoring deliberately weighs <em>density</em> over <em>size</em>. Ten
 * customers who all share devices with each other are a ring. Ten customers in
 * a chain, each linked only to the next, are usually a payment path through a
 * normal population.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RingDetectionService {

    /** Edges weaker than this do not merge components. */
    private static final double MIN_EDGE_STRENGTH = 0.40;
    private static final int MIN_RING_SIZE = 3;
    /** Components larger than this are a data problem, not a gang. */
    private static final int MAX_RING_SIZE = 500;

    private final EntityLinkRepository entityLinkRepository;
    private final FraudRingRepository fraudRingRepository;
    private final GraphAnalyticsRepository analytics;

    @Transactional
    public List<FraudRing> detect(UUID orgId) {
        List<EntityLink> links = entityLinkRepository.findStrongLinks(orgId, MIN_EDGE_STRENGTH);
        if (links.isEmpty()) {
            return List.of();
        }

        Map<String, String> parent = new HashMap<>();
        for (EntityLink link : links) {
            union(parent, link.getSourceId(), link.getTargetId());
        }

        Map<String, Set<String>> components = new HashMap<>();
        Map<String, List<EntityLink>> componentEdges = new HashMap<>();
        for (EntityLink link : links) {
            String root = find(parent, link.getSourceId());
            components.computeIfAbsent(root, k -> new TreeSet<>()).add(link.getSourceId());
            components.get(root).add(link.getTargetId());
            componentEdges.computeIfAbsent(root, k -> new ArrayList<>()).add(link);
        }

        List<FraudRing> rings = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : components.entrySet()) {
            Set<String> members = entry.getValue();
            if (members.size() < MIN_RING_SIZE || members.size() > MAX_RING_SIZE) continue;
            rings.add(persist(orgId, members, componentEdges.get(entry.getKey())));
        }

        log.info("Ring detection for org {} found {} rings from {} edges", orgId, rings.size(), links.size());
        return rings;
    }

    private FraudRing persist(UUID orgId, Set<String> members, List<EntityLink> edges) {
        List<String> memberList = new ArrayList<>(members);
        String ringKey = hash(memberList);

        int n = memberList.size();
        int possibleEdges = n * (n - 1) / 2;
        double density = possibleEdges == 0 ? 0 : (double) edges.size() / possibleEdges;

        BigDecimal exposure = analytics.exposureOf(orgId, memberList);
        long flagged = analytics.flaggedCountOf(orgId, memberList);

        List<String> linkTypes = edges.stream()
                .map(e -> e.getLinkType().name())
                .distinct()
                .sorted()
                .toList();

        int score = scoreRing(n, density, edges, flagged);

        FraudRing ring = fraudRingRepository.findByOrganizationIdAndRingKey(orgId, ringKey)
                .orElseGet(() -> FraudRing.builder()
                        .organizationId(orgId)
                        .ringKey(ringKey)
                        .status(RingStatus.DETECTED)
                        .build());

        ring.setLabel(describe(memberList, linkTypes));
        ring.setMembers(memberList);
        ring.setLinkTypes(linkTypes);
        ring.setMemberCount(n);
        ring.setEdgeCount(edges.size());
        ring.setDensity(Math.round(density * 1000) / 1000.0);
        ring.setRiskScore(score);
        ring.setTotalExposure(exposure == null ? BigDecimal.ZERO : exposure);
        return fraudRingRepository.save(ring);
    }

    private int scoreRing(int size, double density, List<EntityLink> edges, long flaggedTransactions) {
        double avgStrength = edges.stream().mapToDouble(EntityLink::getStrength).average().orElse(0);

        int densityPoints = (int) Math.round(density * 40);
        int strengthPoints = (int) Math.round(avgStrength * 25);
        int sizePoints = Math.min(15, size);
        int flaggedPoints = flaggedTransactions == 0 ? 0 : (int) Math.min(20, 5 + flaggedTransactions);

        return Math.min(100, densityPoints + strengthPoints + sizePoints + flaggedPoints);
    }

    private String describe(List<String> members, List<String> linkTypes) {
        String via = linkTypes.isEmpty() ? "shared attributes" : String.join(", ", linkTypes).toLowerCase(Locale.ROOT);
        return members.size() + " accounts connected by " + via.replace('_', ' ');
    }

    @Transactional
    public FraudRing updateStatus(UUID orgId, UUID ringId, RingStatus status) {
        FraudRing ring = fraudRingRepository.findByIdAndOrganizationId(ringId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown ring " + ringId));
        ring.setStatus(status);
        return fraudRingRepository.save(ring);
    }

    /* ---------- Union-find ---------- */

    private String find(Map<String, String> parent, String node) {
        parent.putIfAbsent(node, node);
        String root = node;
        while (!root.equals(parent.get(root))) {
            root = parent.get(root);
        }
        // Path compression keeps repeated lookups on large components cheap.
        String cursor = node;
        while (!cursor.equals(root)) {
            String next = parent.get(cursor);
            parent.put(cursor, root);
            cursor = next;
        }
        return root;
    }

    private void union(Map<String, String> parent, String a, String b) {
        String rootA = find(parent, a);
        String rootB = find(parent, b);
        if (!rootA.equals(rootB)) {
            parent.put(rootA, rootB);
        }
    }

    /**
     * A ring's identity is its membership, so the same gang keeps the same row
     * across detection runs and an analyst's status decision is not lost every
     * time the sweep runs again.
     */
    private String hash(List<String> members) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(String.join("|", members).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
