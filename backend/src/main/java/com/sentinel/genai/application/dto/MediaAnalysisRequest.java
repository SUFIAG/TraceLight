package com.sentinel.genai.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Signals extracted from media by the capture pipeline or a specialist vendor.
 *
 * <p>The platform scores and decides; it does not do signal processing. That
 * division keeps the decision logic auditable and lets an institution swap its
 * detection vendor without changing a single policy.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAnalysisRequest {

    /** VOICE, VIDEO, DOCUMENT, IMAGE. */
    @NotBlank
    private String mediaType;

    /** CALL_CENTRE, VIDEO_KYC, CHAT_UPLOAD, EMAIL, ONBOARDING. */
    @NotBlank
    private String channel;

    private String subjectId;
    private String mediaReference;

    /* ---- Voice ---- */

    /** Vocoder and neural-synthesis artifacts in the spectrum, 0-100. */
    private Integer spectralArtifactScore;
    /** Breathing, lip noise, and room tone absent — synthetic speech is too clean. */
    private Boolean missingProsodyNoise;
    /** Similarity to the enrolled voiceprint, 0-100. */
    private Integer voiceprintMatchScore;
    /** The audio has no measurable room acoustics, as with direct injection. */
    private Boolean noRoomAcoustics;
    /** Replay of previously captured audio rather than a live speaker. */
    private Boolean replayDetected;

    /* ---- Video ---- */

    /** Face-swap and reenactment artifacts across frames, 0-100. */
    private Integer frameArtifactScore;
    /** Blinking cadence outside the human range. */
    private Boolean abnormalBlinkRate;
    /** Lighting on the face inconsistent with the background. */
    private Boolean lightingInconsistency;
    /** Audio and lip movement out of alignment. */
    private Integer lipSyncErrorMs;
    /** The feed came from a virtual camera rather than a physical one. */
    private Boolean virtualCameraDetected;

    /* ---- Document ---- */

    /** Diffusion and GAN artifacts in the rendered document, 0-100. */
    private Integer generativeArtifactScore;
    /** Text layer fonts do not match any known issuer template. */
    private Boolean fontMismatch;
    /** The PDF producer string names a generative tool. */
    private String producerSoftware;
    /** Numbers in the document do not reconcile — totals, balances, or dates. */
    private Boolean arithmeticInconsistent;
    /** Metadata creation date is after the claimed statement period. */
    private Boolean metadataDateAnomaly;
}
