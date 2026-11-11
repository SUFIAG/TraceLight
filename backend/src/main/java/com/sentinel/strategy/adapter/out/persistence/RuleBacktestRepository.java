package com.sentinel.strategy.adapter.out.persistence;

import com.sentinel.strategy.domain.model.RuleBacktest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RuleBacktestRepository extends JpaRepository<RuleBacktest, UUID> {

    Page<RuleBacktest> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    List<RuleBacktest> findByRuleIdOrderByCreatedAtDesc(UUID ruleId);
}
