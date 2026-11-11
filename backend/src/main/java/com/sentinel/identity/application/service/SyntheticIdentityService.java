package com.sentinel.identity.application.service;

import com.sentinel.identity.application.dto.VerifyIdentityRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Synthetic identity detection.
 *
 * <p>A synthetic identity is not a stolen one. Nobody is impersonated, so no
 * victim ever complains, and every individual attribute can be perfectly
 * valid. What gives it away is the <em>relationship</em> between attributes
 * and claimed age: a 42-year-old with an eight-month credit file, a
 * three-week-old email address, and no social presence has not been living
 * the life the application describes.
 *
 * <p>This is also why synthetic identities survive onboarding and surface two
 * years later as a bust-out. The detection has to happen here or not at all.
 */
@Service
public class SyntheticIdentityService {

    /** Below this, an adult's credit history is inconsistent with their claimed age. */
    private static final int THIN_FILE_MONTHS = 24;

    public int score(VerifyIdentityRequest request, List<String> reasons) {
        VerifyIdentityRequest.FootprintSignals footprint = request.getFootprint();
        if (footprint == null) return 0;

        int score = 0;
        Integer claimedAge = footprint.getClaimedAgeYears();
        Integer fileAge = footprint.getCreditFileAgeMonths();

        if (claimedAge != null && fileAge != null && claimedAge >= 25) {
            int expectedMonths = Math.min(120, (claimedAge - 18) * 12);
            if (fileAge < THIN_FILE_MONTHS && expectedMonths > THIN_FILE_MONTHS) {
                reasons.add("Claimed age " + claimedAge + " but only " + fileAge + " months of credit history");
                score += 45;
            } else if (fileAge < expectedMonths / 3) {
                reasons.add("Credit history far shorter than the claimed age implies");
                score += 25;
            }
        }

        Integer emailAge = footprint.getEmailAgeDays();
        if (emailAge != null && emailAge < 30) {
            reasons.add("Email address is " + emailAge + " days old");
            score += 25;
        }

        if (Boolean.TRUE.equals(footprint.getDisposableEmailDomain())) {
            reasons.add("Email uses a disposable domain");
            score += 30;
        }

        Integer social = footprint.getSocialProfilesFound();
        if (social != null && social == 0 && claimedAge != null && claimedAge < 60) {
            reasons.add("No social or public presence found for this identity");
            score += 20;
        }

        Integer breaches = footprint.getBreachAppearances();
        // Counter-intuitive but reliable: a real identity has been in a breach
        // somewhere. An identity that appears in none has no history at all.
        if (breaches != null && breaches == 0 && emailAge != null && emailAge > 365) {
            reasons.add("Email is over a year old yet appears in no breach corpus");
            score += 10;
        }

        Integer addresses = footprint.getAddressHistoryCount();
        if (addresses != null && addresses <= 1 && claimedAge != null && claimedAge >= 35) {
            reasons.add("Only one address on record for a " + claimedAge + "-year-old applicant");
            score += 15;
        }

        return Math.min(100, score);
    }
}
