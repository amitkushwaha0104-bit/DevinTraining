package com.capstone.fraud.api.dto;

import java.time.Instant;
import java.util.List;

public record RecommendationResponse(
        String caseId,
        String recommendationId,
        String mode,
        String classification,
        String riskLevel,
        String recommendedRoute,
        double confidence,
        boolean humanReviewRequired,
        List<String> evidenceIds,
        List<Citation> policyCitations,
        List<String> missingInformation,
        List<String> ruleIds,
        List<String> toolTraceIds,
        String rationale,
        Instant createdAt) {

    public record Citation(
            String documentId,
            String version,
            String section,
            String chunkId,
            double score,
            String passage) {
    }
}
