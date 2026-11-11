package com.sentinel.identity.application.dto;

import com.sentinel.identity.domain.model.VerificationType;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Everything a verification vendor or the client SDK can tell us about an
 * applicant. Every block is optional: an institution that only runs document
 * checks still gets a document decision, and the dimensions it does not send
 * contribute nothing rather than defaulting to "clean".
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyIdentityRequest {

    @NotBlank
    private String subjectId;

    private String fullName;
    private String dateOfBirth;
    private String country;
    private String email;
    private String phone;

    private VerificationType type;

    private DocumentSignals document;
    private BiometricSignals biometrics;
    private FootprintSignals footprint;
    private TelcoSignals telco;

    /** Identity-document forensics. Scores are 0-100, higher meaning more suspicious. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DocumentSignals {
        /** PASSPORT, NATIONAL_ID, DRIVING_LICENCE, UTILITY_BILL, BANK_STATEMENT, PAYSLIP. */
        private String documentType;
        private String issuingCountry;
        private Boolean mrzChecksumValid;
        /** Glyph shapes inconsistent with the issuer's specimen — the classic forgery tell. */
        private Integer fontAnomalyScore;
        /** Editing software left its signature in the file metadata. */
        private Boolean editorMetadataPresent;
        /** Recompression inconsistent across regions: part of the image was replaced. */
        private Integer compressionArtifactScore;
        /** Frequency-domain artifacts typical of GAN and diffusion output. */
        private Integer generativeArtifactScore;
        private Boolean expired;
        /** The image came from a screen or a printout rather than a physical document. */
        private Boolean screenRecapture;
    }

    /** Selfie, liveness, and injection-attack signals. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BiometricSignals {
        private Boolean livenessPassed;
        /** 0-100 similarity between the selfie and the document portrait. */
        private Integer faceMatchScore;
        /** A virtual camera fed frames straight into the capture pipeline. */
        private Boolean virtualCameraDetected;
        private Boolean emulatorDetected;
        /** Frame-level artifacts consistent with a face-swap or reenactment model. */
        private Integer deepfakeArtifactScore;
        /** The same face has already been enrolled under another identity. */
        private Boolean faceSeenOnOtherIdentity;
    }

    /** Digital footprint enrichment. A real adult leaves traces; a fabricated one does not. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FootprintSignals {
        private Integer emailAgeDays;
        private Boolean disposableEmailDomain;
        private Integer socialProfilesFound;
        private Integer breachAppearances;
        /** Months of credit-bureau history. A thin file on a claimed 40-year-old is the synthetic-identity signature. */
        private Integer creditFileAgeMonths;
        private Integer addressHistoryCount;
        private Integer claimedAgeYears;
    }

    /** Telecommunications signals, strongest around account takeover and recovery flows. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TelcoSignals {
        private Integer simSwapDaysAgo;
        private Integer numberPortedDaysAgo;
        private Integer phoneTenureDays;
        /** The carrier's registered subscriber name does not match the applicant. */
        private Boolean carrierNameMismatch;
        private Boolean voipNumber;
    }
}
