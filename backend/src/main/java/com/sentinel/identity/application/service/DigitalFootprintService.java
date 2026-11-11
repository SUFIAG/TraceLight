package com.sentinel.identity.application.service;

import com.sentinel.identity.application.dto.VerifyIdentityRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Scores how thin an applicant's digital footprint is, independently of the
 * synthetic-identity reasoning that also reads these signals. Kept separate
 * because a thin footprint is common and usually innocent — recent migrants,
 * young adults, privacy-conscious people — so it belongs in the decision as a
 * modest contributor rather than as evidence of fabrication.
 */
@Service
public class DigitalFootprintService {

    public int score(VerifyIdentityRequest.FootprintSignals signals, List<String> reasons) {
        if (signals == null) return 0;
        int score = 0;

        Integer emailAge = signals.getEmailAgeDays();
        if (emailAge != null) {
            if (emailAge < 7) {
                reasons.add("Email created in the last week");
                score += 35;
            } else if (emailAge < 90) {
                score += 15;
            }
        }

        Integer social = signals.getSocialProfilesFound();
        if (social != null) {
            if (social == 0) score += 20;
            else if (social >= 3) score -= 10;
        }

        Integer breaches = signals.getBreachAppearances();
        if (breaches != null && breaches >= 5) {
            reasons.add("Credentials appear in " + breaches + " known breaches — elevated takeover risk");
            score += 20;
        }

        return Math.max(0, Math.min(100, score));
    }
}
