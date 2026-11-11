package com.sentinel.scam.application.service;

import com.sentinel.mule.application.service.BeneficiaryRiskService;
import com.sentinel.scam.adapter.out.persistence.ScamSignalRepository;
import com.sentinel.scam.adapter.out.persistence.VulnerableCustomerRepository;
import com.sentinel.scam.application.dto.ScamAssessmentRequest;
import com.sentinel.scam.application.dto.ScamAssessmentResponse;
import com.sentinel.scam.domain.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Scam-in-progress detection.
 *
 * <p>The hard problem with authorized push payment fraud is that the customer
 * is genuine, the device is genuine, the credentials are genuine, and the
 * payment is genuinely authorized. Nothing a classical fraud engine looks at
 * is wrong. What <em>is</em> wrong is the behaviour around the payment: the
 * victim is on the phone to the criminal, reading details someone dictated,
 * hesitating in places a routine payment never hesitates.
 *
 * <p>Three independent lenses, each 0-100, combined by weight:
 *
 * <pre>
 *   coaching       0.45   is somebody guiding this person right now
 *   beneficiary    0.35   is the destination account already known to be bad
 *   vulnerability  0.20   would this customer be harmed more than most
 * </pre>
 *
 * <p>A confirmed mule beneficiary short-circuits the arithmetic. No weighting
 * of a victim's hesitation should be able to argue a payment into an account
 * another bank has already confirmed as criminal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScamDetectionService {

    private static final double COACHING_WEIGHT = 0.45;
    private static final double BENEFICIARY_WEIGHT = 0.35;
    private static final double VULNERABILITY_WEIGHT = 0.20;

    private final ScamSignalRepository scamSignalRepository;
    private final VulnerableCustomerRepository vulnerableCustomerRepository;
    private final BeneficiaryRiskService beneficiaryRiskService;
    private final CoolingOffService coolingOffService;
    private final ScamTypologyClassifier classifier;

    @Transactional
    public ScamAssessmentResponse assess(UUID orgId, ScamAssessmentRequest request) {
        List<String> indicators = new ArrayList<>();

        int coaching = scoreCoaching(request, indicators);
        int beneficiary = scoreBeneficiary(orgId, request, indicators);
        int vulnerability = scoreVulnerability(orgId, request, indicators);

        boolean confirmedMule = beneficiaryRiskService.isConfirmedMule(orgId, request.getBeneficiaryId());

        int score = confirmedMule ? 100 : (int) Math.round(
                coaching * COACHING_WEIGHT
                        + beneficiary * BENEFICIARY_WEIGHT
                        + vulnerability * VULNERABILITY_WEIGHT);
        score = Math.max(0, Math.min(100, score));

        ScamTypology typology = classifier.classify(request, indicators);
        ScamAction action = decideAction(score, confirmedMule, vulnerability, indicators);

        ScamSignal signal = scamSignalRepository.save(ScamSignal.builder()
                .organizationId(orgId)
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .beneficiaryId(request.getBeneficiaryId())
                .sessionId(request.getSessionId())
                .amount(request.getAmount())
                .typology(typology)
                .score(score)
                .coachingScore(coaching)
                .beneficiaryScore(beneficiary)
                .vulnerabilityScore(vulnerability)
                .indicators(indicators.isEmpty() ? List.of("No scam indicators detected") : indicators)
                .action(action)
                .build());

        ScamAssessmentResponse.ScamAssessmentResponseBuilder response = ScamAssessmentResponse.builder()
                .signalId(signal.getId())
                .score(score)
                .coachingScore(coaching)
                .beneficiaryScore(beneficiary)
                .vulnerabilityScore(vulnerability)
                .typology(typology)
                .typologyLabel(typology.getLabel())
                .action(action)
                .indicators(signal.getIndicators())
                .warning(action == ScamAction.ALLOW ? null : typology.getWarningCopy());

        if (action == ScamAction.COOLING_OFF || action == ScamAction.TRUSTED_CONTACT) {
            PaymentHold hold = coolingOffService.hold(orgId, request, signal, action == ScamAction.TRUSTED_CONTACT);
            response.holdId(hold.getId())
                    .releaseAt(hold.getReleaseAt())
                    .trustedContactNotified(hold.isTrustedContactNotified());
        }

        log.info("Scam assessment for user {}: score={} typology={} action={}",
                request.getUserId(), score, typology, action);
        return response.build();
    }

    /**
     * Coaching signals. Each is weak alone — plenty of honest customers pause,
     * and plenty take calls while banking. Together they describe a person who
     * is being talked through a payment by someone else.
     */
    private int scoreCoaching(ScamAssessmentRequest request, List<String> indicators) {
        int score = 0;

        if (Boolean.TRUE.equals(request.getRemoteAccessDetected())) {
            indicators.add("Remote-access software is running during the payment");
            score += 45;
        }
        if (Boolean.TRUE.equals(request.getActiveCallDuringSession())) {
            indicators.add("Customer is on a call while making the payment");
            score += 25;
        }
        if (Boolean.TRUE.equals(request.getPayeeDetailsPasted())) {
            indicators.add("Payee details were pasted, not typed");
            score += 15;
        }

        Integer hesitation = request.getHesitationSeconds();
        if (hesitation != null) {
            if (hesitation >= 180) {
                indicators.add("Customer spent " + hesitation + "s on the confirmation screen");
                score += 20;
            } else if (hesitation >= 90) {
                indicators.add("Customer spent " + hesitation + "s on the confirmation screen");
                score += 10;
            }
        }

        Integer switches = request.getAppSwitchCount();
        if (switches != null && switches >= 4) {
            indicators.add("Customer switched apps " + switches + " times during the payment flow");
            score += 12;
        }

        Integer edits = request.getReferenceEditCount();
        if (edits != null && edits >= 3) {
            indicators.add("Payment reference was changed " + edits + " times");
            score += 8;
        }

        return Math.min(100, score);
    }

    private int scoreBeneficiary(UUID orgId, ScamAssessmentRequest request, List<String> indicators) {
        int score = 0;

        int muleScore = beneficiaryRiskService.riskOf(orgId, request.getBeneficiaryId());
        if (muleScore >= 80) {
            indicators.add("Destination account shows strong mule characteristics (score " + muleScore + ")");
            score += 60;
        } else if (muleScore >= 50) {
            indicators.add("Destination account is on the mule watchlist (score " + muleScore + ")");
            score += 35;
        }

        if (Boolean.TRUE.equals(request.getFirstTimePayee())) {
            indicators.add("First payment to this payee");
            score += 15;
        }

        Integer payeeAge = request.getPayeeAgeDays();
        if (payeeAge != null && payeeAge <= 1) {
            indicators.add("Payee was added today");
            score += 20;
        } else if (payeeAge != null && payeeAge <= 7) {
            indicators.add("Payee was added " + payeeAge + " days ago");
            score += 10;
        }

        String cop = request.getConfirmationOfPayee();
        if ("NO_MATCH".equalsIgnoreCase(cop)) {
            indicators.add("Confirmation of Payee returned no match on the account name");
            score += 30;
        } else if ("CLOSE_MATCH".equalsIgnoreCase(cop)) {
            indicators.add("Confirmation of Payee returned only a close match");
            score += 12;
        }

        if ("CRYPTO".equalsIgnoreCase(request.getRail())) {
            indicators.add("Payment leaves on an irreversible crypto rail");
            score += 15;
        }

        return Math.min(100, score);
    }

    private int scoreVulnerability(UUID orgId, ScamAssessmentRequest request, List<String> indicators) {
        int score = 0;

        Optional<VulnerableCustomer> flagged = vulnerableCustomerRepository
                .findByOrganizationIdAndUserIdAndActiveTrue(orgId, request.getUserId());
        if (flagged.isPresent()) {
            int level = flagged.get().getProtectionLevel();
            indicators.add("Customer is under adapted protection (level " + level + ")");
            score += Math.min(60, level * 20);
        }

        Integer age = request.getCustomerAgeYears();
        if (age != null && age >= 75) {
            indicators.add("Customer is " + age + " years old");
            score += 30;
        } else if (age != null && age >= 65) {
            indicators.add("Customer is " + age + " years old");
            score += 15;
        }

        if (Boolean.TRUE.equals(request.getPriorScamVictim())) {
            indicators.add("Customer has been a scam victim before");
            score += 25;
        }

        BigDecimal amount = request.getAmount();
        if (amount != null && amount.compareTo(new BigDecimal("10000")) >= 0) {
            indicators.add("Payment is large enough to cause material harm");
            score += 20;
        }

        return Math.min(100, score);
    }

    /**
     * The action ladder. A hard block is last, not first: blocked victims are
     * routinely re-coached and pay again through a channel with no controls.
     * Slowing the payment down and putting a second person in the loop stops
     * more scams than refusing it does.
     */
    private ScamAction decideAction(int score, boolean confirmedMule, int vulnerability, List<String> indicators) {
        if (confirmedMule) {
            indicators.add("Destination is a confirmed mule account — payment refused");
            return ScamAction.BLOCK;
        }
        if (score >= 85) return ScamAction.HUMAN_REVIEW;
        if (score >= 70) return vulnerability >= 50 ? ScamAction.TRUSTED_CONTACT : ScamAction.COOLING_OFF;
        if (score >= 50) return ScamAction.WARN_AND_CONFIRM;
        if (score >= 30) return ScamAction.WARN;
        return ScamAction.ALLOW;
    }
}
