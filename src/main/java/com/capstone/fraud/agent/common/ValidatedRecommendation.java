package com.capstone.fraud.agent.common;

import com.capstone.fraud.domain.Classification;
import com.capstone.fraud.domain.RecommendedRoute;
import com.capstone.fraud.domain.RiskLevel;

import java.util.List;

/**
 * Parsed, schema-valid recommendation. Only this type may be persisted
 * or rendered as an approved recommendation.
 */
public record ValidatedRecommendation(
        String caseId,
        Classification classification,
        RiskLevel riskLevel,
        RecommendedRoute recommendedRoute,
        double confidence,
        boolean humanReviewRequired,
        List<String> evidenceIds,
        List<PolicyCitationRef> policyCitations,
        List<String> missingInformation,
        List<String> ruleIds,
        List<String> toolTraceIds,
        String rationale) {

    public record PolicyCitationRef(
            String documentId,
            String version,
            String section,
            String chunkId,
            double score) {
    }
}
