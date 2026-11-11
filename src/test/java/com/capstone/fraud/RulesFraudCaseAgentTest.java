package com.capstone.fraud;

import com.capstone.fraud.agent.common.*;
import com.capstone.fraud.agent.rules.RulesFraudCaseAgent;
import com.capstone.fraud.knowledge.PolicyPassage;
import com.capstone.fraud.knowledge.PolicyRetriever;
import com.capstone.fraud.persistence.AuditEventEntity;
import com.capstone.fraud.persistence.AuditWriter;
import com.capstone.fraud.tools.DeterministicFixtures;
import com.capstone.fraud.tools.EvidenceTools;
import com.capstone.fraud.tools.ToolResults.PolicyEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RulesFraudCaseAgentTest {

    private EvidenceTools tools;
    private RulesFraudCaseAgent agent;
    private final RecommendationValidator validator = new RecommendationValidator();

    @BeforeEach
    void setUp() {
        var fixtures = new DeterministicFixtures("fraud-training-v1");
        var retriever = mock(PolicyRetriever.class);
        var audit = mock(AuditWriter.class);
        var event = new AuditEventEntity();
        event.setEventId("AE-1");
        event.setEventType("TEST");
        event.setRecordedAt(Instant.now());
        when(audit.record(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(event);
        tools = new EvidenceTools(fixtures, retriever, audit);
        agent = new RulesFraudCaseAgent();
    }

    private FraudCaseContext context(String txnRef, String customerRef, String issueType,
                                     boolean merchantRecognised, List<PolicyPassage> passages) {
        Map<String, Object> results = new LinkedHashMap<>();
        results.put("getTransactionDetails", tools.getTransactionDetails(txnRef));
        results.put("getDeviceSignals", tools.getDeviceSignals(customerRef, txnRef));
        results.put("compareRecentLocations", tools.compareRecentLocations(customerRef, txnRef));
        results.put("getMerchantRiskProfile", tools.getMerchantRiskProfile("MERCHANT ••••11", txnRef));
        results.put("getPriorDisputes", tools.getPriorDisputes(customerRef, txnRef));
        results.put("retrievePolicyEvidence", new PolicyEvidence("TRES-9", Instant.now(), passages.isEmpty(),
                passages.stream().map(p -> new PolicyEvidence.Passage(
                        p.documentId(), p.version(), p.section(), p.chunkId(), p.score(), p.passage())).toList()));

        var intake = new IntakeSnapshot(customerRef, "IN-MH", txnRef, new BigDecimal("100"), "INR",
                Instant.now(), "POSTED", "CARD_NOT_PRESENT", "MERCHANT ••••11", issueType,
                "I do not recognise this purchase and still have my card.", true, merchantRecognised);
        return new FraudCaseContext("CASE-1", intake,
                List.of(new EvidenceSnapshot("EV-1", "DEVICE", "HIGH", "s", "TOOL_RESULT", Instant.now())),
                passages, List.of(new ToolTraceSnapshot("TR-1", "getTransactionDetails", "SUCCESS", 5, "ok")),
                "", results);
    }

    private PolicyPassage passage() {
        return new PolicyPassage("POL-CNP-017", "CNP", "3.2", "3.2 Initial fraud routing",
                "cnp017-002", "2026-10-01", 0.5, "route to fraud investigation");
    }

    private FraudCaseRecommendation analyze(FraudCaseContext ctx) {
        var raw = agent.analyze(ctx);
        // contract check for every scenario - throws if schema-invalid
        validator.validate(raw, ctx);
        return raw;
    }

    @Test
    void t01UnauthorizedProfileRoutesToFraud() {
        var ctx = context("TXN-2026-784102", "CUST-••••-4821", "MERCHANT_NOT_RECOGNISED", false, List.of(passage()));
        var r = analyze(ctx);
        assertEquals("UNAUTHORISED_TRANSACTION", r.getClassification());
        assertEquals("FRAUD_INVESTIGATION", r.getRecommendedRoute());
        assertEquals("HIGH", r.getRiskLevel());
        assertTrue(r.getHumanReviewRequired());
        assertTrue(r.getRuleIds().contains("R-03"));
        assertFalse(r.getPolicyCitations().isEmpty());
    }

    @Test
    void t02RecognisedMerchantRoutesToDisputeOps() {
        var ctx = context("TXN-2026-MRC01", "CUST-2", "CANCELLATION_PROBLEM", true, List.of(passage()));
        var r = analyze(ctx);
        assertEquals("MERCHANT_DISPUTE", r.getClassification());
        assertEquals("DISPUTE_OPERATIONS", r.getRecommendedRoute());
        assertTrue(r.getRuleIds().contains("R-04"));
    }

    @Test
    void t03DuplicateFingerprintRoutesToPaymentOps() {
        var ctx = context("TXN-2026-DUP01", "CUST-3", "DUPLICATE_CHARGE", false, List.of(passage()));
        var r = analyze(ctx);
        assertEquals("DUPLICATE_PROCESSING", r.getClassification());
        assertEquals("PAYMENT_OPERATIONS", r.getRecommendedRoute());
        assertTrue(r.getRuleIds().contains("R-02"));
    }

    @Test
    void t04MissingTransactionYieldsInsufficientEvidence() {
        var ctx = context("TXN-2026-MISSING", "CUST-4", "MERCHANT_NOT_RECOGNISED", false, List.of(passage()));
        var r = analyze(ctx);
        assertEquals("INSUFFICIENT_EVIDENCE", r.getClassification());
        assertEquals("MANUAL_REVIEW", r.getRecommendedRoute());
        assertTrue(r.getMissingInformation().contains("transaction record unavailable"));
        assertTrue(r.getRuleIds().contains("R-01"));
    }

    @Test
    void t05NoPolicyPassageForcesManualReviewWithoutPolicyClaim() {
        var ctx = context("TXN-2026-784102", "CUST-5", "MERCHANT_NOT_RECOGNISED", false, List.of());
        var r = analyze(ctx);
        assertEquals("MANUAL_REVIEW", r.getRecommendedRoute());
        assertTrue(r.getPolicyCitations().isEmpty());
        assertTrue(r.getMissingInformation().stream().anyMatch(m -> m.contains("policy")));
        assertTrue(r.getRuleIds().contains("R-05"));
    }

    @Test
    void alwaysRequiresHumanReview() {
        for (String ref : List.of("TXN-2026-784102", "TXN-2026-DUP01", "TXN-2026-MRC01", "TXN-2026-MISSING")) {
            var r = analyze(context(ref, "CUST-9", "MERCHANT_NOT_RECOGNISED", false, List.of(passage())));
            assertTrue(r.getHumanReviewRequired(), ref);
        }
    }
}
