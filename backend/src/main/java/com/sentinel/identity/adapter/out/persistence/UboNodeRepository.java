package com.sentinel.identity.adapter.out.persistence;

import com.sentinel.identity.domain.model.UboNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UboNodeRepository extends JpaRepository<UboNode, UUID> {

    List<UboNode> findByBusinessEntityIdOrderByDepthAsc(UUID businessEntityId);

    List<UboNode> findByBusinessEntityIdAndIsUboTrue(UUID businessEntityId);

    void deleteByBusinessEntityId(UUID businessEntityId);
}
