package com.sentinel.consortium.adapter.out.persistence;

import com.sentinel.consortium.domain.model.ConsortiumSignal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsortiumSignalRepository extends JpaRepository<ConsortiumSignal, UUID> {

    Optional<ConsortiumSignal> findByHashedIdentifierAndSignalType(String hashedIdentifier, String signalType);

    List<ConsortiumSignal> findByHashedIdentifier(String hashedIdentifier);

    Page<ConsortiumSignal> findByContributorCountGreaterThanEqualOrderByLastSeenDesc(
            int minContributors, Pageable pageable);

    @Query("SELECT s.signalType, COUNT(s), COALESCE(AVG(s.severity), 0) FROM ConsortiumSignal s " +
            "WHERE s.contributorCount >= :minContributors GROUP BY s.signalType ORDER BY COUNT(s) DESC")
    List<Object[]> summaryByType(@Param("minContributors") int minContributors);

    long countByContributorCountGreaterThanEqual(int minContributors);
}
