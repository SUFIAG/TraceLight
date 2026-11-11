package com.sentinel.scam.application.service;

import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.scam.adapter.out.persistence.PaymentHoldRepository;
import com.sentinel.scam.adapter.out.persistence.TrustedContactRepository;
import com.sentinel.scam.application.dto.ScamAssessmentRequest;
import com.sentinel.scam.domain.model.HoldStatus;
import com.sentinel.scam.domain.model.PaymentHold;
import com.sentinel.scam.domain.model.ScamSignal;
import com.sentinel.scam.domain.model.TrustedContact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cooling-off holds and trusted-contact notification.
 *
 * <p>Delay is the cheapest scam control that exists. The criminal's whole
 * method depends on urgency, and a payment the customer has to come back to
 * tomorrow is a payment they usually do not make. The hold length scales with
 * the assessed score rather than being a flat policy, because a four-hour
 * delay on every unusual payment would be a worse customer experience than
 * the fraud it prevents.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CoolingOffService {

    private final PaymentHoldRepository paymentHoldRepository;
    private final TrustedContactRepository trustedContactRepository;

    @Transactional
    public PaymentHold hold(UUID orgId, ScamAssessmentRequest request, ScamSignal signal, boolean notifyContact) {
        Duration delay = delayFor(signal.getScore());

        PaymentHold hold = PaymentHold.builder()
                .organizationId(orgId)
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .beneficiaryId(request.getBeneficiaryId())
                .amount(request.getAmount())
                .scamSignalId(signal.getId())
                .status(HoldStatus.HELD)
                .reason(signal.getTypology().getLabel() + " suspected (score " + signal.getScore() + ")")
                .releaseAt(Instant.now().plus(delay))
                .trustedContactNotified(false)
                .build();

        if (notifyContact) {
            hold.setTrustedContactNotified(notifyTrustedContacts(orgId, request));
        }

        PaymentHold saved = paymentHoldRepository.save(hold);
        log.info("Payment held for {} until {} — {}", request.getUserId(), saved.getReleaseAt(), saved.getReason());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<PaymentHold> listActive(UUID orgId) {
        return paymentHoldRepository.findByOrganizationIdAndStatusOrderByReleaseAtAsc(orgId, HoldStatus.HELD);
    }

    /** An analyst satisfied the payment is genuine. The customer gets their money moved now. */
    @Transactional
    public PaymentHold release(UUID orgId, UUID holdId, UUID actorId) {
        PaymentHold hold = load(orgId, holdId);
        hold.setStatus(HoldStatus.RELEASED);
        hold.setResolvedAt(Instant.now());
        hold.setResolvedBy(actorId);
        return paymentHoldRepository.save(hold);
    }

    /** The scam was confirmed, by the customer or by an analyst. The payment never leaves. */
    @Transactional
    public PaymentHold cancel(UUID orgId, UUID holdId, UUID actorId, boolean byCustomer) {
        PaymentHold hold = load(orgId, holdId);
        hold.setStatus(byCustomer ? HoldStatus.CANCELLED_BY_CUSTOMER : HoldStatus.CANCELLED_BY_ANALYST);
        hold.setResolvedAt(Instant.now());
        hold.setResolvedBy(actorId);
        return paymentHoldRepository.save(hold);
    }

    /**
     * Releases holds whose cooling-off window has passed with no human decision.
     * A hold that silently never expires would be a payment the bank has
     * quietly refused without telling anyone.
     */
    @Scheduled(fixedDelayString = "${sentinel.scam.hold-sweep-ms:60000}")
    @Transactional
    public void releaseExpiredHolds() {
        List<PaymentHold> due = paymentHoldRepository.findByStatusAndReleaseAtBefore(HoldStatus.HELD, Instant.now());
        if (due.isEmpty()) return;
        for (PaymentHold hold : due) {
            hold.setStatus(HoldStatus.EXPIRED_AND_RELEASED);
            hold.setResolvedAt(Instant.now());
        }
        paymentHoldRepository.saveAll(due);
        log.info("Released {} payment holds whose cooling-off window elapsed", due.size());
    }

    private Duration delayFor(int score) {
        if (score >= 90) return Duration.ofHours(24);
        if (score >= 80) return Duration.ofHours(4);
        return Duration.ofMinutes(30);
    }

    private boolean notifyTrustedContacts(UUID orgId, ScamAssessmentRequest request) {
        List<TrustedContact> contacts =
                trustedContactRepository.findByOrganizationIdAndUserIdAndActiveTrue(orgId, request.getUserId());
        BigDecimal amount = request.getAmount() == null ? BigDecimal.ZERO : request.getAmount();

        List<TrustedContact> toNotify = contacts.stream()
                .filter(c -> c.getNotifyAboveAmount() == null || amount.compareTo(c.getNotifyAboveAmount()) >= 0)
                .toList();

        // The notification itself goes out through the outreach channel; recording
        // that it was due is what the reimbursement case later turns on.
        toNotify.forEach(c -> log.info("Trusted contact {} ({}) notified for customer {}",
                c.getContactName(), c.getContactChannel(), request.getUserId()));
        return !toNotify.isEmpty();
    }

    private PaymentHold load(UUID orgId, UUID holdId) {
        return paymentHoldRepository.findByIdAndOrganizationId(holdId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentHold", holdId.toString()));
    }
}
