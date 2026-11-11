package com.sentinel.graph.application.service;

import com.sentinel.graph.adapter.out.persistence.EntityLinkRepository;
import com.sentinel.graph.adapter.out.persistence.GraphAnalyticsRepository;
import com.sentinel.graph.domain.model.EntityLink;
import com.sentinel.graph.domain.model.LinkType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Turns transaction rows into graph edges.
 *
 * <p>The build is attribute-driven: find every device, IP, and beneficiary
 * touched by more than one customer, then connect those customers.
 *
 * <p>{@code MAX_FAN_OUT} is the detail that makes this usable. A popular
 * merchant is touched by thousands of customers and would produce a clique
 * with millions of edges that links the entire customer base into one
 * meaningless "ring". Any attribute shared by more than a few dozen customers
 * is a shared facility, not a shared conspiracy, so it produces no edges.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntityResolutionService {

    /** Attributes above this fan-out are public infrastructure, not evidence. */
    private static final long MAX_FAN_OUT = 25;
    private static final int LOOKBACK_DAYS = 90;

    private final GraphAnalyticsRepository analytics;
    private final EntityLinkRepository entityLinkRepository;

    /**
     * Rebuilds the edge set for an organization.
     *
     * @return number of edges created or reinforced
     */
    @Transactional
    public int rebuild(UUID orgId) {
        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        int edges = 0;

        edges += link(orgId, analytics.sharedDevices(orgId, since, MAX_FAN_OUT),
                LinkType.SHARED_DEVICE, value -> analytics.customersByDevice(orgId, value, since));

        edges += link(orgId, analytics.sharedIps(orgId, since, MAX_FAN_OUT),
                LinkType.SHARED_IP, value -> analytics.customersByIp(orgId, value, since));

        edges += link(orgId, analytics.sharedBeneficiaries(orgId, since, MAX_FAN_OUT),
                LinkType.SHARED_BENEFICIARY, value -> analytics.customersByBeneficiary(orgId, value, since));

        log.info("Entity resolution for org {} produced {} edges", orgId, edges);
        return edges;
    }

    private int link(UUID orgId, List<String> values, LinkType type,
                     Function<String, List<String>> customersOf) {
        int created = 0;
        for (String value : values) {
            List<String> customers = customersOf.apply(value);
            if (customers.size() < 2) continue;
            for (int i = 0; i < customers.size(); i++) {
                for (int j = i + 1; j < customers.size(); j++) {
                    upsert(orgId, customers.get(i), customers.get(j), type, value);
                    created++;
                }
            }
        }
        return created;
    }

    /**
     * Writes the edge with endpoints in a canonical order so (a,b) and (b,a)
     * are the same row. Repeated observations reinforce the edge instead of
     * duplicating it.
     */
    @Transactional
    public EntityLink upsert(UUID orgId, String a, String b, LinkType type, String viaValue) {
        String source = a.compareTo(b) <= 0 ? a : b;
        String target = a.compareTo(b) <= 0 ? b : a;

        EntityLink link = entityLinkRepository
                .findByOrganizationIdAndSourceIdAndTargetIdAndLinkType(orgId, source, target, type)
                .orElseGet(() -> EntityLink.builder()
                        .organizationId(orgId)
                        .sourceType("CUSTOMER")
                        .sourceId(source)
                        .targetType("CUSTOMER")
                        .targetId(target)
                        .linkType(type)
                        .observationCount(0)
                        .firstSeen(Instant.now())
                        .build());

        link.setViaValue(viaValue);
        link.setObservationCount(link.getObservationCount() + 1);
        link.setLastSeen(Instant.now());
        // Repetition adds confidence but with diminishing returns: the second
        // sighting of a shared device matters, the twentieth does not.
        link.setStrength(Math.min(1.0, type.getWeight() * (1 + Math.log10(link.getObservationCount()))));
        return entityLinkRepository.save(link);
    }

    @Transactional(readOnly = true)
    public List<EntityLink> neighbours(UUID orgId, String entityId) {
        return entityLinkRepository.findNeighbours(orgId, entityId);
    }

    /** Clears the graph so it can be rebuilt from scratch after a data correction. */
    @Transactional
    public void reset(UUID orgId) {
        entityLinkRepository.deleteByOrganizationId(orgId);
        log.warn("Entity graph for org {} cleared", orgId);
    }

    /** Breadth-first expansion from one entity, bounded by depth. */
    @Transactional(readOnly = true)
    public List<EntityLink> expand(UUID orgId, String entityId, int depth) {
        List<EntityLink> collected = new ArrayList<>();
        Set<UUID> seenEdges = new HashSet<>();
        Set<String> visited = new HashSet<>();
        List<String> frontier = new ArrayList<>(List.of(entityId));

        for (int level = 0; level < Math.max(1, Math.min(depth, 3)); level++) {
            List<String> next = new ArrayList<>();
            for (String node : frontier) {
                if (!visited.add(node)) continue;
                for (EntityLink link : entityLinkRepository.findNeighbours(orgId, node)) {
                    if (seenEdges.add(link.getId())) collected.add(link);
                    next.add(link.getSourceId().equals(node) ? link.getTargetId() : link.getSourceId());
                }
            }
            frontier = next;
        }
        return collected;
    }
}
