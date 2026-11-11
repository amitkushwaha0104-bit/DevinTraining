package com.capstone.fraud.agent.common;

import com.capstone.fraud.domain.Classification;
import com.capstone.fraud.domain.RecommendedRoute;
import com.capstone.fraud.domain.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Enforces the structured-output contract before a recommendation is
 * persisted or displayed. Rejects unknown enums, out-of-range
 * confidence, missing human-review flag and malformed citations.
 */
@Component
public class RecommendationValidator {

    private static final Pattern PROCEDURAL_CLAIM =
            Pattern.compile("(?i)\\b(policy|procedure|pol-|section\\s*\\d|regulation|mandated)\\b");

    public ValidatedRecommendation validate(FraudCaseRecommendation raw, FraudCaseContext context) {
        List<String> violations = new ArrayList<>();

        if (raw == null) {
            throw new InvalidRecommendationException(List.of("recommendation is null"));
        }

        if (raw.getCaseId() == null || raw.getCaseId().isBlank()) {
            violations.add("caseId is required");
        } else if (context != null && !raw.getCaseId().equals(context.caseId())) {
            violations.add("caseId does not match the investigated case");
        }

        Classification classification = parseEnum(Classification.class, raw.getClassification(), "classification", violations);
        RiskLevel risk = parseEnum(RiskLevel.class, raw.getRiskLevel(), "riskLevel", violations);
        RecommendedRoute route = parseEnum(RecommendedRoute.class, raw.getRecommendedRoute(), "recommendedRoute", violations);

        if (raw.getConfidence() == null || raw.getConfidence() < 0.0 || raw.getConfidence() > 1.0) {
            violations.add("confidence must be between 0 and 1");
        }

        // The assistant is advisory only. This flag can never be waived.
        if (!Boolean.TRUE.equals(raw.getHumanReviewRequired())) {
            violations.add("humanReviewRequired must be true for every recommendation");
        }

        List<String> evidenceIds = nullToEmpty(raw.getEvidenceIds());
        if (evidenceIds.isEmpty() && classification != Classification.INSUFFICIENT_EVIDENCE) {
            violations.add("at least one evidence item is required unless the outcome is INSUFFICIENT_EVIDENCE");
        }

        List<ValidatedRecommendation.PolicyCitationRef> citations = new ArrayList<>();
        if (raw.getPolicyCitations() != null) {
            for (FraudCaseRecommendation.PolicyCitationRef c : raw.getPolicyCitations()) {
                if (c == null || blank(c.getDocumentId()) || blank(c.getVersion())
                        || blank(c.getSection()) || blank(c.getChunkId())
                        || c.getScore() == null || c.getScore() < 0.0 || c.getScore() > 1.0) {
                    violations.add("malformed policy citation");
                    continue;
                }
                citations.add(new ValidatedRecommendation.PolicyCitationRef(
                        c.getDocumentId(), c.getVersion(), c.getSection(), c.getChunkId(), c.getScore()));
            }
        }

        String rationale = raw.getRationale() == null ? "" : raw.getRationale().trim();
        if (rationale.isBlank()) {
            violations.add("rationale is required");
        } else if (PROCEDURAL_CLAIM.matcher(rationale).find() && citations.isEmpty()) {
            violations.add("rationale claims a procedural requirement but provides no policy citation");
        }

        if (!violations.isEmpty()) {
            throw new InvalidRecommendationException(violations);
        }

        return new ValidatedRecommendation(
                raw.getCaseId(),
                classification,
                risk,
                route,
                raw.getConfidence(),
                true,
                List.copyOf(evidenceIds),
                List.copyOf(citations),
                List.copyOf(nullToEmpty(raw.getMissingInformation())),
                List.copyOf(nullToEmpty(raw.getRuleIds())),
                List.copyOf(nullToEmpty(raw.getToolTraceIds())),
                rationale);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field, List<String> violations) {
        if (value == null || value.isBlank()) {
            violations.add(field + " is required");
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException e) {
            violations.add("unknown " + field + " value: " + value);
            return null;
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static List<String> nullToEmpty(List<String> list) {
        return list == null ? List.of() : list;
    }
}
