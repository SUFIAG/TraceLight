package com.sentinel.identity.application.service;

import com.sentinel.identity.application.dto.VerifyIdentityRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Liveness and deepfake scoring, kept as two numbers rather than one.
 *
 * <p>They fail differently. A presentation attack — a printed photo, a mask —
 * is caught by liveness. An <em>injection</em> attack bypasses the camera
 * entirely and feeds synthesised frames to the app, so the liveness check
 * passes perfectly while the face was never in the room. Collapsing both into
 * a single "biometric score" hides exactly the attack that is growing.
 */
@Service
@RequiredArgsConstructor
public class BiometricIntegrityService {

    public Result score(VerifyIdentityRequest.BiometricSignals signals, List<String> reasons) {
        if (signals == null) return new Result(0, 0);

        int liveness = 0;
        int deepfake = 0;

        if (Boolean.FALSE.equals(signals.getLivenessPassed())) {
            reasons.add("Liveness check failed");
            liveness += 60;
        }

        Integer match = signals.getFaceMatchScore();
        if (match != null && match < 50) {
            reasons.add("Selfie does not match the document portrait (" + match + "/100)");
            liveness += 50;
        } else if (match != null && match < 70) {
            reasons.add("Selfie match to the document portrait is weak (" + match + "/100)");
            liveness += 20;
        }

        if (Boolean.TRUE.equals(signals.getEmulatorDetected())) {
            reasons.add("Capture came from an emulator, not a physical device");
            liveness += 35;
        }

        if (Boolean.TRUE.equals(signals.getVirtualCameraDetected())) {
            reasons.add("A virtual camera injected frames into the capture pipeline");
            deepfake += 70;
        }

        Integer artifacts = signals.getDeepfakeArtifactScore();
        if (artifacts != null && artifacts >= 70) {
            reasons.add("Frame analysis shows strong face-synthesis artifacts (" + artifacts + "/100)");
            deepfake += 70;
        } else if (artifacts != null && artifacts >= 40) {
            reasons.add("Frame analysis shows possible face-synthesis artifacts (" + artifacts + "/100)");
            deepfake += 35;
        }

        if (Boolean.TRUE.equals(signals.getFaceSeenOnOtherIdentity())) {
            reasons.add("This face is already enrolled under a different identity");
            deepfake += 40;
        }

        return new Result(Math.min(100, liveness), Math.min(100, deepfake));
    }

    @Getter
    public static class Result {
        private final int livenessScore;
        private final int deepfakeScore;

        public Result(int livenessScore, int deepfakeScore) {
            this.livenessScore = livenessScore;
            this.deepfakeScore = deepfakeScore;
        }
    }
}
