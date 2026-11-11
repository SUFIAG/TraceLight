package com.sentinel.identity.application.service;

import com.sentinel.identity.application.dto.VerifyIdentityRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Telco signals. The decisive one is SIM-swap recency: every
 * SMS one-time-password in the institution is only as strong as the claim
 * that the phone still belongs to the customer, and a swap in the last
 * 24 hours invalidates that claim entirely.
 */
@Service
public class TelcoIntelligenceService {

    public int score(VerifyIdentityRequest.TelcoSignals signals, List<String> reasons) {
        if (signals == null) return 0;
        int score = 0;

        Integer swap = signals.getSimSwapDaysAgo();
        if (swap != null) {
            if (swap <= 1) {
                reasons.add("SIM was swapped in the last 24 hours");
                score += 70;
            } else if (swap <= 7) {
                reasons.add("SIM was swapped " + swap + " days ago");
                score += 45;
            } else if (swap <= 30) {
                score += 15;
            }
        }

        Integer ported = signals.getNumberPortedDaysAgo();
        if (ported != null && ported <= 7) {
            reasons.add("Number was ported to a new carrier " + ported + " days ago");
            score += 35;
        }

        Integer tenure = signals.getPhoneTenureDays();
        if (tenure != null && tenure < 30) {
            reasons.add("Phone number is less than a month old");
            score += 25;
        }

        if (Boolean.TRUE.equals(signals.getCarrierNameMismatch())) {
            reasons.add("Carrier subscriber name does not match the applicant");
            score += 30;
        }

        if (Boolean.TRUE.equals(signals.getVoipNumber())) {
            reasons.add("Number is VoIP, which cannot be tied to a physical subscriber");
            score += 20;
        }

        return Math.min(100, score);
    }
}
