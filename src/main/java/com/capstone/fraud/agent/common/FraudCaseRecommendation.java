package com.capstone.fraud.agent.common;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

import java.util.List;

/**
 * Structured recommendation contract. Enum-typed fields are kept as
 * strings at the wire boundary so unknown model output is rejected by
 * application validation instead of a deserialization failure.
 */
@Data
public class FraudCaseRecommendation {

    @JsonPropertyDescription("The case identifier this recommendation applies to.")
    private String caseId;

    @JsonPropertyDescription("One of: UNAUTHORISED_TRANSACTION, MERCHANT_DISPUTE, DUPLICATE_PROCESSING, SUSPICIOUS_TRANSACTION, INSUFFICIENT_EVIDENCE, GENUINE_TRANSACTION")
    private String classification;

    @JsonPropertyDescription("One of: LOW, MEDIUM, HIGH")
    private String riskLevel;

    @JsonPropertyDescription("One of: FRAUD_INVESTIGATION, DISPUTE_OPERATIONS, PAYMENT_OPERATIONS, MANUAL_REVIEW")
    private String recommendedRoute;

    @JsonPropertyDescription("Confidence between 0.0 and 1.0")
    private Double confidence;

    @JsonPropertyDescription("Must always be true. The recommendation is advisory; a human reviewer makes the final disposition.")
    private Boolean humanReviewRequired;

    @JsonPropertyDescription("Evidence item identifiers supporting the rationale.")
    private List<String> evidenceIds;

    @JsonPropertyDescription("Policy citations backing any procedural claim.")
    private List<PolicyCitationRef> policyCitations;

    @JsonPropertyDescription("Information that is missing, contradictory or unavailable.")
    private List<String> missingInformation;

    @JsonPropertyDescription("Deterministic rule identifiers when rules mode produced this result.")
    private List<String> ruleIds;

    @JsonPropertyDescription("Tool trace identifiers that produced the cited evidence.")
    private List<String> toolTraceIds;

    @JsonPropertyDescription("Concise evidence-linked rationale. No hidden reasoning.")
    private String rationale;

    @Data
    public static class PolicyCitationRef {
        private String documentId;
        private String version;
        private String section;
        private String chunkId;
        private Double score;
    }
}
