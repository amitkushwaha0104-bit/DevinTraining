package com.capstone.fraud;

import com.capstone.fraud.agent.common.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationValidatorTest {

    private final RecommendationValidator validator = new RecommendationValidator();

    private FraudCaseContext context() {
        var intake = new IntakeSnapshot("CUST-••••-4821", "IN-MH", "TXN-1", BigDecimal.TEN, "INR",
                Instant.now(), "POSTED", "CARD_NOT_PRESENT", "M ••••1", "OTHER", "statement", true, false);
        return new FraudCaseContext("CASE-2026-0042", intake, List.of(
                new EvidenceSnapshot("EV-1", "DEVICE", "HIGH", "new device", "TOOL_RESULT", Instant.now())),
                List.of(), List.of(), "", Map.of());
    }

    private FraudCaseRecommendation valid() {
        var r = new FraudCaseRecommendation();
        r.setCaseId("CASE-2026-0042");
        r.setClassification("SUSPICIOUS_TRANSACTION");
        r.setRiskLevel("MEDIUM");
        r.setRecommendedRoute("MANUAL_REVIEW");
        r.setConfidence(0.5);
        r.setHumanReviewRequired(true);
        r.setEvidenceIds(List.of("EV-1"));
        r.setPolicyCitations(List.of());
        r.setMissingInformation(List.of());
        r.setRationale("Based on tool evidence EV-1.");
        return r;
    }

    @Test
    void acceptsValidRecommendation() {
        ValidatedRecommendation v = validator.validate(valid(), context());
        assertEquals("CASE-2026-0042", v.caseId());
        assertTrue(v.humanReviewRequired());
    }

    @Test
    void t09RejectsUnknownEnum() {
        var r = valid();
        r.setClassification("NOT_A_REAL_CLASS");
        var ex = assertThrows(InvalidRecommendationException.class, () -> validator.validate(r, context()));
        assertTrue(ex.violations().stream().anyMatch(v -> v.contains("unknown classification")));
    }

    @Test
    void rejectsWhenHumanReviewFalse() {
        var r = valid();
        r.setHumanReviewRequired(false);
        assertThrows(InvalidRecommendationException.class, () -> validator.validate(r, context()));
    }

    @Test
    void rejectsOutOfRangeConfidence() {
        var r = valid();
        r.setConfidence(1.5);
        assertThrows(InvalidRecommendationException.class, () -> validator.validate(r, context()));
    }

    @Test
    void rejectsCaseIdMismatch() {
        var r = valid();
        r.setCaseId("CASE-OTHER");
        assertThrows(InvalidRecommendationException.class, () -> validator.validate(r, context()));
    }

    @Test
    void rejectsMalformedCitation() {
        var r = valid();
        var c = new FraudCaseRecommendation.PolicyCitationRef();
        c.setDocumentId("POL-CNP-017");
        r.setPolicyCitations(List.of(c));
        assertThrows(InvalidRecommendationException.class, () -> validator.validate(r, context()));
    }

    @Test
    void rejectsProceduralClaimWithoutCitation() {
        var r = valid();
        r.setRationale("Policy POL-CNP-017 section 3.2 mandates fraud routing.");
        assertThrows(InvalidRecommendationException.class, () -> validator.validate(r, context()));
    }

    @Test
    void allowsInsufficientEvidenceWithoutEvidenceIds() {
        var r = valid();
        r.setClassification("INSUFFICIENT_EVIDENCE");
        r.setEvidenceIds(List.of());
        r.setMissingInformation(List.of("transaction record unavailable"));
        assertDoesNotThrow(() -> validator.validate(r, context()));
    }
}
