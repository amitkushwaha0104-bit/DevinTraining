package com.capstone.fraud.agent.rules;

import com.capstone.fraud.agent.common.*;
import com.capstone.fraud.domain.IssueType;
import com.capstone.fraud.tools.ToolResults.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic fallback processor. Applies auditable rules to the
 * validated intake facts and structured tool results. Every result
 * carries the rule IDs fired and the facts used; it never consults a
 * model and always requires human review.
 */
@Component
public class RulesFraudCaseAgent implements FraudCaseProcessor {

    @Override
    public FraudCaseRecommendation analyze(FraudCaseContext context) {
        Set<String> rules = new LinkedHashSet<>();
        List<String> missing = new ArrayList<>();
        List<String> facts = new ArrayList<>();

        var txn = context.toolResult("getTransactionDetails", TransactionDetails.class);
        var device = context.toolResult("getDeviceSignals", DeviceSignals.class);
        var location = context.toolResult("compareRecentLocations", LocationComparison.class);
        var merchant = context.toolResult("getMerchantRiskProfile", MerchantRiskProfile.class);
        var disputes = context.toolResult("getPriorDisputes", PriorDisputes.class);
        var policy = context.toolResult("retrievePolicyEvidence", PolicyEvidence.class);

        boolean txnFound = txn != null && txn.found();
        boolean statementPresent = context.intake().customerStatement() != null
                && !context.intake().customerStatement().isBlank();

        String classification;
        String route;
        String risk;
        double confidence;

        // R-01: mandatory evidence missing -> insufficient evidence
        if (!txnFound || !statementPresent) {
            rules.add("R-01");
            if (!txnFound) {
                missing.add("transaction record unavailable");
            }
            if (!statementPresent) {
                missing.add("customer statement missing");
            }
            classification = "INSUFFICIENT_EVIDENCE";
            route = "MANUAL_REVIEW";
            risk = "MEDIUM";
            confidence = 0.35;
            facts.add("transactionFound=" + txnFound);
            facts.add("statementPresent=" + statementPresent);
        } else {
            List<String> highSignals = independentHighSignals(device, location, merchant, facts);
            boolean unauthorisedPattern = context.intake().issueType().equals(IssueType.MERCHANT_NOT_RECOGNISED.name())
                    && !context.intake().merchantRecognised();

            if (Boolean.TRUE.equals(txn.duplicateFingerprintMatch())) {
                // R-02: confirmed duplicate fingerprint -> payment operations
                rules.add("R-02");
                classification = "DUPLICATE_PROCESSING";
                route = "PAYMENT_OPERATIONS";
                risk = "MEDIUM";
                confidence = 0.95;
                facts.add("duplicateFingerprintMatch=true");
            } else if (highSignals.size() >= 2) {
                // R-03: >=2 independent high-severity anomalies -> fraud queue
                rules.add("R-03");
                classification = unauthorisedPattern ? "UNAUTHORISED_TRANSACTION" : "SUSPICIOUS_TRANSACTION";
                route = "FRAUD_INVESTIGATION";
                risk = "HIGH";
                confidence = Math.min(0.95, 0.6 + highSignals.size() * 0.12);
                facts.add("highSeveritySignals=" + highSignals);
            } else if (context.intake().merchantRecognised() && isMerchantComplaint(context.intake().issueType())) {
                // R-04: recognised merchant + fulfilment/cancellation/amount -> dispute ops
                rules.add("R-04");
                classification = "MERCHANT_DISPUTE";
                route = "DISPUTE_OPERATIONS";
                risk = "LOW";
                confidence = 0.8;
                facts.add("merchantRecognised=true issueType=" + context.intake().issueType());
            } else if (unauthorisedPattern && !highSignals.isEmpty()) {
                rules.add("R-03");
                classification = "SUSPICIOUS_TRANSACTION";
                route = "FRAUD_INVESTIGATION";
                risk = "MEDIUM";
                confidence = 0.55;
                facts.add("highSeveritySignals=" + highSignals);
            } else {
                // R-06: no fraud signal pattern -> likely genuine, still human-reviewed
                rules.add("R-06");
                classification = "GENUINE_TRANSACTION";
                route = "MANUAL_REVIEW";
                risk = "LOW";
                confidence = 0.5;
                facts.add("noFraudPatternDetected=true");
            }
        }

        // R-05: never invent policy - no passage above threshold forces manual review
        boolean policyAvailable = policy != null && !policy.abstained() && !policy.passages().isEmpty();
        if (!policyAvailable && !"INSUFFICIENT_EVIDENCE".equals(classification)) {
            rules.add("R-05");
            missing.add("no policy passage met the retrieval threshold");
            route = "MANUAL_REVIEW";
            confidence = Math.min(confidence, 0.4);
            facts.add("policyPassages=0");
        }
        if (!policyAvailable && "INSUFFICIENT_EVIDENCE".equals(classification)) {
            missing.add("no policy passage met the retrieval threshold");
        }

        FraudCaseRecommendation recommendation = new FraudCaseRecommendation();
        recommendation.setCaseId(context.caseId());
        recommendation.setClassification(classification);
        recommendation.setRiskLevel(risk);
        recommendation.setRecommendedRoute(route);
        recommendation.setConfidence(confidence);
        recommendation.setHumanReviewRequired(true);
        recommendation.setEvidenceIds(evidenceIds(context));
        recommendation.setPolicyCitations(citations(policy));
        recommendation.setMissingInformation(missing);
        recommendation.setRuleIds(List.copyOf(rules));
        recommendation.setToolTraceIds(traceIds(context));
        recommendation.setRationale(buildRationale(classification, route, facts));
        return recommendation;
    }

    private List<String> independentHighSignals(DeviceSignals device, LocationComparison location,
                                                MerchantRiskProfile merchant, List<String> facts) {
        List<String> signals = new ArrayList<>();
        if (device != null && !device.deviceKnown()) {
            signals.add("newly-observed-device");
            facts.add("deviceKnown=false trustLevel=" + device.trustLevel());
        }
        if (location != null && "HIGH".equals(location.anomalyLevel())) {
            signals.add("high-location-anomaly");
            facts.add("distanceKm=" + location.distanceKm());
        }
        if (merchant != null && (merchant.blocklistMatch() || "HIGH".equals(merchant.riskBand()))) {
            signals.add("high-risk-merchant");
            facts.add("merchantRiskBand=" + merchant.riskBand() + " blocklist=" + merchant.blocklistMatch());
        }
        return signals;
    }

    private static boolean isMerchantComplaint(String issueType) {
        return IssueType.FULFILMENT_PROBLEM.name().equals(issueType)
                || IssueType.CANCELLATION_PROBLEM.name().equals(issueType)
                || IssueType.AMOUNT_DISPUTE.name().equals(issueType);
    }

    private List<String> evidenceIds(FraudCaseContext context) {
        return context.evidence() == null ? List.of()
                : context.evidence().stream().map(EvidenceSnapshot::evidenceId).toList();
    }

    private List<String> traceIds(FraudCaseContext context) {
        return context.toolTraces() == null ? List.of()
                : context.toolTraces().stream().map(ToolTraceSnapshot::traceId).toList();
    }

    private List<FraudCaseRecommendation.PolicyCitationRef> citations(PolicyEvidence policy) {
        if (policy == null || policy.passages() == null) {
            return List.of();
        }
        return policy.passages().stream().map(p -> {
            var ref = new FraudCaseRecommendation.PolicyCitationRef();
            ref.setDocumentId(p.documentId());
            ref.setVersion(p.version());
            ref.setSection(p.section());
            ref.setChunkId(p.chunkId());
            ref.setScore(p.score());
            return ref;
        }).toList();
    }

    private String buildRationale(String classification, String route, List<String> facts) {
        return "Rules-mode assessment classified the case as %s and recommends the %s route, "
                .formatted(classification, route)
                + "based on validated intake and tool evidence: " + String.join(", ", facts) + ". "
                + "This is advisory only; a human reviewer makes the final disposition.";
    }
}
