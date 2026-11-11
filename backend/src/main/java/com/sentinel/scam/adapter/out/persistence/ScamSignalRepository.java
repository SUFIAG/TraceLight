package com.sentinel.scam.adapter.out.persistence;

import com.sentinel.scam.domain.model.ScamSignal;
import com.sentinel.scam.domain.model.ScamTypology;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScamSignalRepository extends JpaRepository<ScamSignal, UUID> {

    Page<ScamSignal> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    List<ScamSignal> findByOrganizationIdAndUserIdOrderByCreatedAtDesc(UUID organizationId, String userId);

    Page<ScamSignal> findByOrganizationIdAndTypologyOrderByCreatedAtDesc(UUID organizationId,
                                                                        ScamTypology typology,
                                                                        Pageable pageable);

    @Query("SELECT s.typology, COUNT(s), COALESCE(SUM(s.amount), 0) FROM ScamSignal s " +
            "WHERE s.organizationId = :orgId AND s.createdAt > :since AND s.score >= :minScore " +
            "GROUP BY s.typology ORDER BY COUNT(s) DESC")
    List<Object[]> breakdownByTypology(@Param("orgId") UUID orgId,
                                       @Param("since") Instant since,
                                       @Param("minScore") int minScore);

    @Query("SELECT COUNT(s) FROM ScamSignal s WHERE s.organizationId = :orgId " +
            "AND s.createdAt > :since AND s.action <> com.sentinel.scam.domain.model.ScamAction.ALLOW")
    long countInterventionsSince(@Param("orgId") UUID orgId, @Param("since") Instant since);
}
