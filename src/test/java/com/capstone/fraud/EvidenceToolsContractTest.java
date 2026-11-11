package com.capstone.fraud;

import com.capstone.fraud.knowledge.PolicyRetriever;
import com.capstone.fraud.persistence.AuditEventEntity;
import com.capstone.fraud.persistence.AuditWriter;
import com.capstone.fraud.tools.DeterministicFixtures;
import com.capstone.fraud.tools.EvidenceTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tool contract: deterministic output, provenance fields, structured
 * not-found handling - the same across the whole catalogue.
 */
class EvidenceToolsContractTest {

    private EvidenceTools tools;

    @BeforeEach
    void setUp() {
        var retriever = mock(PolicyRetriever.class);
        var audit = mock(AuditWriter.class);
        var event = new AuditEventEntity();
        event.setEventId("AE-1");
        event.setEventType("TEST");
        event.setRecordedAt(Instant.now());
        when(audit.record(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(event);
        tools = new EvidenceTools(new DeterministicFixtures("fraud-training-v1"), retriever, audit);
    }

    @Test
    void transactionToolIsDeterministic() {
        var a = tools.getTransactionDetails("TXN-2026-784102");
        var b = tools.getTransactionDetails("TXN-2026-784102");
        // ids differ (sequence), everything else identical
        assertEquals(a.found(), b.found());
        assertEquals(a.status(), b.status());
        assertEquals(a.authOutcome(), b.authOutcome());
        assertEquals(a.amount(), b.amount());
        assertEquals(a.duplicateFingerprintMatch(), b.duplicateFingerprintMatch());
        assertNotNull(a.toolResultId());
        assertNotNull(a.observedAt());
    }

    @Test
    void missingRefReturnsStructuredNotFound() {
        var r = tools.getTransactionDetails("TXN-2026-MISSING");
        assertFalse(r.found());
        assertEquals("UNAVAILABLE", r.authOutcome());
        assertFalse(r.observations().isEmpty());
    }

    @Test
    void deviceToolReturnsTrustAndProvenance() {
        var r = tools.getDeviceSignals("CUST-1", "TXN-2026-784102");
        assertNotNull(r.toolResultId());
        assertNotNull(r.observedAt());
        assertFalse(r.deviceKnown());
        assertNotNull(r.trustLevel());
        assertFalse(r.observations().isEmpty());
    }

    @Test
    void locationToolReturnsDistanceAndAnomaly() {
        var r = tools.compareRecentLocations("CUST-1", "TXN-2026-784102");
        assertTrue(r.distanceKm() > 1000);
        assertEquals("HIGH", r.anomalyLevel());
    }

    @Test
    void merchantToolReturnsRiskBand() {
        var r = tools.getMerchantRiskProfile("NORTHSTAR DIGITAL ••••73", "TXN-2026-784102");
        assertEquals("HIGH", r.riskBand());
        assertFalse(r.blocklistMatch());
    }

    @Test
    void slaToolReturnsQueueAndTarget() {
        var r = tools.calculateCaseSla("FRAUD_INVESTIGATION", "HIGH");
        assertEquals("FRAUD_INVESTIGATION", r.queue());
        assertEquals("P1", r.priority());
        assertTrue(r.targetAt().isAfter(Instant.now()));
    }

    @Test
    void auditToolAppendsEvent() {
        var r = tools.saveAuditEvent("CASE-1", "NOTE", "test note");
        assertNotNull(r.eventId());
        assertNotNull(r.recordedAt());
    }

    @Test
    void policyToolReturnsStructuredResult() {
        var r = tools.retrievePolicyEvidence("fraud");
        assertNotNull(r.toolResultId());
        assertNotNull(r.passages());
    }
}
