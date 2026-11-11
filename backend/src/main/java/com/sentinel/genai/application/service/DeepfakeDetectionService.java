package com.sentinel.genai.application.service;

import com.sentinel.genai.adapter.out.persistence.MediaAnalysisRepository;
import com.sentinel.genai.application.dto.MediaAnalysisRequest;
import com.sentinel.genai.domain.model.MediaAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Synthetic media detection across voice, video, and documents.
 *
 * <p>Generative tools removed the two assumptions every remote verification
 * control rested on: that a voice on a call belongs to a person, and that a
 * document that looks right was issued by somebody. Neither holds now, and the
 * attacks that follow — the cloned voice authorising a transfer, the executive
 * on a video call who is not there — defeat controls that were considered
 * strong two years ago.
 *
 * <p>Each modality scores separately because they fail differently, but all
 * three converge on one verdict and one action, so the call-centre agent and
 * the onboarding analyst read the same thing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeepfakeDetectionService {

    private final MediaAnalysisRepository repository;

    @Transactional
    public MediaAnalysis analyse(UUID orgId, MediaAnalysisRequest request) {
        List<String> indicators = new ArrayList<>();
        String type = request.getMediaType().toUpperCase(Locale.ROOT);

        int score = switch (type) {
            case "VOICE" -> scoreVoice(request, indicators);
            case "VIDEO" -> scoreVideo(request, indicators);
            case "DOCUMENT", "IMAGE" -> scoreDocument(request, indicators);
            default -> 0;
        };

        String verdict = verdictOf(score, indicators);
        String action = actionOf(score, request.getChannel());

        MediaAnalysis analysis = repository.save(MediaAnalysis.builder()
                .organizationId(orgId)
                .mediaType(type)
                .subjectId(request.getSubjectId())
                .channel(request.getChannel())
                .mediaReference(request.getMediaReference())
                .syntheticScore(score)
                .verdict(verdict)
                .indicators(indicators.isEmpty() ? List.of("No synthetic-media indicators") : indicators)
                .action(action)
                .build());

        log.info("{} media from {} scored {} — {} ({})",
                type, request.getChannel(), score, verdict, action);
        return analysis;
    }

    /**
     * Voice cloning. The strongest tells are absences rather than artifacts:
     * synthesised speech has no breath, no room, and no microphone.
     */
    private int scoreVoice(MediaAnalysisRequest request, List<String> indicators) {
        int score = 0;

        Integer spectral = request.getSpectralArtifactScore();
        if (spectral != null && spectral >= 70) {
            indicators.add("Vocoder artifacts in the spectrum (" + spectral + "/100)");
            score += 55;
        } else if (spectral != null && spectral >= 40) {
            indicators.add("Possible synthesis artifacts in the spectrum (" + spectral + "/100)");
            score += 25;
        }

        if (Boolean.TRUE.equals(request.getMissingProsodyNoise())) {
            indicators.add("No breathing or lip noise — the audio is unnaturally clean");
            score += 30;
        }
        if (Boolean.TRUE.equals(request.getNoRoomAcoustics())) {
            indicators.add("No room acoustics, consistent with injected rather than captured audio");
            score += 25;
        }
        if (Boolean.TRUE.equals(request.getReplayDetected())) {
            indicators.add("Audio matches a previously captured recording");
            score += 40;
        }

        Integer match = request.getVoiceprintMatchScore();
        if (match != null && match < 50) {
            indicators.add("Voice does not match the enrolled print (" + match + "/100)");
            score += 35;
        } else if (match != null && match > 97 && spectral != null && spectral >= 40) {
            // A clone is often a *better* match to the enrolled print than the
            // real person, because it was trained on that very recording.
            indicators.add("Voiceprint match is implausibly perfect alongside synthesis artifacts");
            score += 20;
        }

        return Math.min(100, score);
    }

    /** Video calls: face swap, reenactment, and camera injection. */
    private int scoreVideo(MediaAnalysisRequest request, List<String> indicators) {
        int score = 0;

        Integer frames = request.getFrameArtifactScore();
        if (frames != null && frames >= 70) {
            indicators.add("Strong face-synthesis artifacts across frames (" + frames + "/100)");
            score += 55;
        } else if (frames != null && frames >= 40) {
            indicators.add("Possible face-synthesis artifacts across frames (" + frames + "/100)");
            score += 25;
        }

        if (Boolean.TRUE.equals(request.getVirtualCameraDetected())) {
            indicators.add("Feed originates from a virtual camera");
            score += 50;
        }
        if (Boolean.TRUE.equals(request.getAbnormalBlinkRate())) {
            indicators.add("Blink cadence outside the human range");
            score += 20;
        }
        if (Boolean.TRUE.equals(request.getLightingInconsistency())) {
            indicators.add("Face lighting inconsistent with the background");
            score += 20;
        }

        Integer lipSync = request.getLipSyncErrorMs();
        if (lipSync != null && lipSync > 120) {
            indicators.add("Lip movement lags the audio by " + lipSync + "ms");
            score += 25;
        }

        return Math.min(100, score);
    }

    /** Documents produced wholesale by a generative model rather than edited. */
    private int scoreDocument(MediaAnalysisRequest request, List<String> indicators) {
        int score = 0;

        Integer generative = request.getGenerativeArtifactScore();
        if (generative != null && generative >= 70) {
            indicators.add("Strong generative-model artifacts (" + generative + "/100)");
            score += 55;
        } else if (generative != null && generative >= 40) {
            indicators.add("Possible generative-model artifacts (" + generative + "/100)");
            score += 25;
        }

        if (Boolean.TRUE.equals(request.getArithmeticInconsistent())) {
            // A generated bank statement looks right and does not add up.
            indicators.add("Figures in the document do not reconcile");
            score += 45;
        }
        if (Boolean.TRUE.equals(request.getFontMismatch())) {
            indicators.add("Fonts do not match any known issuer template");
            score += 30;
        }
        if (Boolean.TRUE.equals(request.getMetadataDateAnomaly())) {
            indicators.add("File was created after the period it claims to cover");
            score += 30;
        }

        String producer = request.getProducerSoftware();
        if (producer != null && isGenerativeProducer(producer)) {
            indicators.add("Producer metadata names a generative tool: " + producer);
            score += 35;
        }

        return Math.min(100, score);
    }

    private boolean isGenerativeProducer(String producer) {
        String lower = producer.toLowerCase(Locale.ROOT);
        return lower.contains("gpt") || lower.contains("diffusion") || lower.contains("midjourney")
                || lower.contains("dall") || lower.contains("generated") || lower.contains("synthesis");
    }

    private String verdictOf(int score, List<String> indicators) {
        if (indicators.isEmpty()) return "AUTHENTIC";
        if (score >= 75) return "SYNTHETIC";
        if (score >= 40) return "SUSPECTED_SYNTHETIC";
        if (score >= 20) return "INCONCLUSIVE";
        return "AUTHENTIC";
    }

    /**
     * The action depends on what the media was being used for. The same score
     * on a video KYC session and on a call-centre authorisation warrant
     * different responses, because one can be retried and the other is money
     * leaving.
     */
    private String actionOf(int score, String channel) {
        boolean highStakes = "CALL_CENTRE".equalsIgnoreCase(channel) || "VIDEO_KYC".equalsIgnoreCase(channel);
        if (score >= 75) return highStakes ? "BLOCK" : "ESCALATE";
        if (score >= 40) return "STEP_UP";
        return "ALLOW";
    }

    @Transactional
    public MediaAnalysis review(UUID orgId, UUID analysisId, String humanVerdict, UUID reviewerId) {
        MediaAnalysis analysis = repository.findByIdAndOrganizationId(analysisId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown media analysis " + analysisId));
        analysis.setHumanVerdict(humanVerdict);
        analysis.setReviewedBy(reviewerId);
        return repository.save(analysis);
    }
}
