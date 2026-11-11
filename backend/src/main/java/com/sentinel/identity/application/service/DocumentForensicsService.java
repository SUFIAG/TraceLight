package com.sentinel.identity.application.service;

import com.sentinel.identity.application.dto.VerifyIdentityRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Document forgery and generative-artifact scoring.
 *
 * <p>Three generations of attack, in the order they appeared and in rising
 * order of how much they matter now:
 *
 * <ol>
 *   <li>Physical forgery — wrong fonts, broken MRZ checksums.</li>
 *   <li>Digital tampering — editor metadata, inconsistent recompression.</li>
 *   <li>Generative fabrication — a document that never existed, produced by a
 *       diffusion model, with no inconsistency to find because nothing was
 *       edited. This is the one legacy document checks fail on, so it carries
 *       the heaviest weight here.</li>
 * </ol>
 */
@Service
public class DocumentForensicsService {

    public int score(VerifyIdentityRequest.DocumentSignals signals, List<String> reasons) {
        if (signals == null) return 0;
        int score = 0;

        Integer generative = signals.getGenerativeArtifactScore();
        if (generative != null && generative >= 70) {
            reasons.add("Document shows strong generative-model artifacts (" + generative + "/100)");
            score += 70;
        } else if (generative != null && generative >= 40) {
            reasons.add("Document shows possible generative-model artifacts (" + generative + "/100)");
            score += 35;
        }

        if (Boolean.FALSE.equals(signals.getMrzChecksumValid())) {
            reasons.add("Machine-readable zone checksum does not validate");
            score += 60;
        }

        Integer font = signals.getFontAnomalyScore();
        if (font != null && font >= 60) {
            reasons.add("Glyph shapes differ from the issuer specimen (" + font + "/100)");
            score += 40;
        }

        if (Boolean.TRUE.equals(signals.getEditorMetadataPresent())) {
            reasons.add("Image metadata shows it passed through editing software");
            score += 30;
        }

        Integer compression = signals.getCompressionArtifactScore();
        if (compression != null && compression >= 60) {
            reasons.add("Recompression is inconsistent across the image — a region was replaced");
            score += 35;
        }

        if (Boolean.TRUE.equals(signals.getScreenRecapture())) {
            reasons.add("Capture is a photograph of a screen, not of a document");
            score += 25;
        }

        if (Boolean.TRUE.equals(signals.getExpired())) {
            reasons.add("Document is expired");
            score += 15;
        }

        return Math.min(100, score);
    }
}
