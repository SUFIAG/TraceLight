package com.sentinel.graph.adapter.out.persistence;

import com.sentinel.graph.domain.model.EntityLink;
import com.sentinel.graph.domain.model.LinkType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EntityLinkRepository extends JpaRepository<EntityLink, UUID> {

    Optional<EntityLink> findByOrganizationIdAndSourceIdAndTargetIdAndLinkType(
            UUID organizationId, String sourceId, String targetId, LinkType linkType);

    List<EntityLink> findByOrganizationId(UUID organizationId);

    @Query("SELECT l FROM EntityLink l WHERE l.organizationId = :orgId " +
            "AND (l.sourceId = :entityId OR l.targetId = :entityId) ORDER BY l.strength DESC")
    List<EntityLink> findNeighbours(@Param("orgId") UUID orgId, @Param("entityId") String entityId);

    @Query("SELECT l FROM EntityLink l WHERE l.organizationId = :orgId AND l.strength >= :minStrength")
    List<EntityLink> findStrongLinks(@Param("orgId") UUID orgId, @Param("minStrength") double minStrength);

    long countByOrganizationId(UUID organizationId);

    void deleteByOrganizationId(UUID organizationId);
}
