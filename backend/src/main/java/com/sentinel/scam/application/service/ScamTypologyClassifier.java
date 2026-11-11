package com.sentinel.scam.application.service;

import com.sentinel.ai.adapter.out.llm.LlmClient;
import com.sentinel.scam.application.dto.ScamAssessmentRequest;
import com.sentinel.scam.domain.model.ScamTypology;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Decides which scam family a payment belongs to.
 *
 * <p>Deterministic keyword scoring first, LLM second. The order matters: the
 * classifier runs inside the payment path, so it has to return an answer with
 * or without a model, and a lexicon built from real scam scripts beats a
 * general model on the short, formulaic text that payment references contain.
 * The LLM is consulted only when the lexicon finds nothing and there is prose
 * worth reading.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScamTypologyClassifier {

    /** Terms harvested from scam scripts circulating on messaging platforms. */
    private static final Map<ScamTypology, List<String>> LEXICON = Map.of(
            ScamTypology.ROMANCE,
            List.of("my love", "darling", "soulmate", "fiance", "stuck abroad", "flight ticket",
                    "customs fee", "never met", "dating"),

            ScamTypology.INVESTMENT,
            List.of("guaranteed return", "profit", "trading", "broker", "portfolio", "fx",
                    "forex", "bond", "high yield", "roi"),

            ScamTypology.IMPERSONATION,
            List.of("police", "hmrc", "irs", "tax office", "fraud team", "security department",
                    "warrant", "officer", "court"),

            ScamTypology.PIG_BUTCHERING,
            List.of("usdt", "tether", "mentor", "signal group", "withdrawal fee", "upgrade account",
                    "vip tier", "liquidity"),

            ScamTypology.INVOICE_REDIRECT,
            List.of("invoice", "supplier", "new bank details", "updated account", "remittance",
                    "purchase order", "accounts payable"),

            ScamTypology.PURCHASE,
            List.of("marketplace", "deposit", "puppy", "car", "ticket", "holiday rental", "deposit hold"),

            ScamTypology.ADVANCE_FEE,
            List.of("release fee", "processing fee", "inheritance", "lottery", "prize", "clearance fee"),

            ScamTypology.SAFE_ACCOUNT,
            List.of("safe account", "move your money", "secure account", "protect your funds"),

            ScamTypology.CRYPTO_RAMP,
            List.of("binance", "coinbase", "wallet address", "btc", "eth", "exchange deposit")
    );

    private final LlmClient llmClient;

    public ScamTypology classify(ScamAssessmentRequest request, List<String> indicators) {
        String corpus = corpusOf(request);

        if (!corpus.isBlank()) {
            Optional<ScamTypology> lexical = byLexicon(corpus);
            if (lexical.isPresent()) {
                indicators.add("Narrative matches the " + lexical.get().getLabel() + " pattern");
                return lexical.get();
            }
        }

        if ("CRYPTO".equalsIgnoreCase(request.getRail())) {
            return ScamTypology.CRYPTO_RAMP;
        }

        Optional<ScamTypology> inferred = byLlm(corpus);
        if (inferred.isPresent()) {
            indicators.add("Narrative classified by the language model as " + inferred.get().getLabel());
            return inferred.get();
        }

        return ScamTypology.UNKNOWN;
    }

    private String corpusOf(ScamAssessmentRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getPaymentReference() != null) sb.append(request.getPaymentReference()).append(' ');
        if (request.getBeneficiaryName() != null) sb.append(request.getBeneficiaryName()).append(' ');
        if (request.getConversationKeywords() != null) {
            sb.append(String.join(" ", request.getConversationKeywords()));
        }
        return sb.toString().toLowerCase(Locale.ROOT).trim();
    }

    private Optional<ScamTypology> byLexicon(String corpus) {
        ScamTypology best = null;
        int bestHits = 0;
        for (Map.Entry<ScamTypology, List<String>> entry : LEXICON.entrySet()) {
            int hits = (int) entry.getValue().stream().filter(corpus::contains).count();
            if (hits > bestHits) {
                bestHits = hits;
                best = entry.getKey();
            }
        }
        return bestHits > 0 ? Optional.of(best) : Optional.empty();
    }

    private Optional<ScamTypology> byLlm(String corpus) {
        if (!llmClient.isEnabled() || corpus.isBlank()) {
            return Optional.empty();
        }
        String system = """
                You classify suspected authorized push payment scams for a bank.
                Reply with exactly one of these tokens and nothing else:
                ROMANCE, INVESTMENT, IMPERSONATION, PIG_BUTCHERING, INVOICE_REDIRECT,
                PURCHASE, ADVANCE_FEE, SAFE_ACCOUNT, CRYPTO_RAMP, UNKNOWN.
                Answer UNKNOWN when the text does not clearly indicate one family.
                """;
        return llmClient.complete(system, "Payment context: " + corpus)
                .map(String::trim)
                .flatMap(token -> {
                    try {
                        return Optional.of(ScamTypology.valueOf(token.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        log.debug("Language model returned an unrecognised typology: {}", token);
                        return Optional.empty();
                    }
                })
                .filter(t -> t != ScamTypology.UNKNOWN);
    }
}
