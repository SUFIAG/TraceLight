package com.sentinel.scam.adapter.out.persistence;

import com.sentinel.scam.domain.model.HoldStatus;
import com.sentinel.scam.domain.model.PaymentHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentHoldRepository extends JpaRepository<PaymentHold, UUID> {

    List<PaymentHold> findByOrganizationIdAndStatusOrderByReleaseAtAsc(UUID organizationId, HoldStatus status);

    Optional<PaymentHold> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<PaymentHold> findByStatusAndReleaseAtBefore(HoldStatus status, Instant cutoff);

    long countByOrganizationIdAndStatus(UUID organizationId, HoldStatus status);
}
