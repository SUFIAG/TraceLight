package com.sentinel.identity.adapter.out.persistence;

import com.sentinel.identity.domain.model.SanctionsListEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SanctionsListEntryRepository extends JpaRepository<SanctionsListEntry, UUID> {

    List<SanctionsListEntry> findByActiveTrue();

    long countByActiveTrue();
}
