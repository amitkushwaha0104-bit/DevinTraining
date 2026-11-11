package com.capstone.fraud.agent.common;

import com.capstone.fraud.knowledge.PolicyPassage;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Everything a processor is allowed to see for one case. Memory and
 * policy context are case-scoped by construction.
 */
public record FraudCaseContext(
        String caseId,
        IntakeSnapshot intake,
        List<EvidenceSnapshot> evidence,
        List<PolicyPassage> policyPassages,
        List<ToolTraceSnapshot> toolTraces,
        String conversationContext,
        java.util.Map<String, Object> toolResults) {

    /** Convenience accessor for a structured tool result by tool name. */
    @SuppressWarnings("unchecked")
    public <T> T toolResult(String toolName, Class<T> type) {
        Object value = toolResults == null ? null : toolResults.get(toolName);
        return type.isInstance(value) ? (T) value : null;
    }

    public static final String INVESTIGATION_GOAL =
            "Classify the dispute, assess risk, and recommend an operational route for human review.";

    public Map<String, Object> promptVariables() {
        return Map.of(
                "caseId", caseId,
                "investigationGoal", INVESTIGATION_GOAL,
                "caseSummary", renderCaseSummary(),
                "toolEvidence", renderToolEvidence(),
                "policyContext", renderPolicyContext(),
                "conversationContext", conversationContext == null ? "" : conversationContext);
    }

    public String renderCaseSummary() {
        IntakeSnapshot i = intake;
        return """
                Case %s
                Customer ref: %s | Home region: %s
                Transaction ref: %s | Amount: %s %s | Occurred: %s
                Status: %s | Channel: %s | Merchant: %s
                Issue type: %s | Card in possession: %s | Merchant recognised: %s
                Customer statement: %s
                """.formatted(
                caseId, i.maskedCustomerRef(), nullToDash(i.homeRegion()),
                i.transactionRef(), i.amount(), i.currency(), i.occurredAt(),
                i.transactionStatus(), i.transactionChannel(), i.maskedMerchant(),
                i.issueType(), i.cardPossession(), i.merchantRecognised(),
                i.customerStatement());
    }

    public String renderToolEvidence() {
        if (evidence == null || evidence.isEmpty()) {
            return "No tool evidence collected.";
        }
        return evidence.stream()
                .map(e -> "[%s] type=%s severity=%s | %s (provenance=%s)"
                        .formatted(e.evidenceId(), e.type(), e.severity(), e.valueSummary(), e.provenance()))
                .collect(Collectors.joining("\n"));
    }

    public String renderPolicyContext() {
        if (policyPassages == null || policyPassages.isEmpty()) {
            return "NO POLICY CONTEXT RETRIEVED - abstain from procedural claims.";
        }
        return policyPassages.stream()
                .map(p -> "doc=%s v%s section=%s chunk=%s score=%.2f | %s"
                        .formatted(p.documentId(), p.version(), p.section(), p.chunkId(), p.score(), p.passage()))
                .collect(Collectors.joining("\n---\n"));
    }

    private static String nullToDash(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }
}
