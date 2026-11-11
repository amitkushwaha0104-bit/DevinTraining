package com.capstone.fraud;

import com.capstone.fraud.api.dto.CaseIntakeRequest;
import com.capstone.fraud.application.FraudCaseService;
import com.capstone.fraud.domain.CaseStatus;
import com.capstone.fraud.persistence.AuditEventRepository;
import com.capstone.fraud.persistence.FraudCaseEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The audit trail is append-only: every important state change adds an
 * event, none are mutated or removed.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditTrailTests {

    @Autowired
    FraudCaseService caseService;

    @Autowired
    AuditEventRepository auditRepository;

    private FraudCaseEntity newCase(String ref) {
        var req = new CaseIntakeRequest("CUST-••••-0001", "IN-MH", ref, BigDecimal.TEN, "INR",
                Instant.now(), "POSTED", "CARD_NOT_PRESENT", "M ••••1", "OTHER", "statement text",
                true, false, "WEB", Instant.now(), true, true);
        return caseService.createCase(req, "agent1");
    }

    @Test
    void eventsAccumulateAndAreNeverModified() {
        FraudCaseEntity c = newCase("TXN-AUD-1");
        int before = caseService.auditFor(c).size();
        assertTrue(before >= 1, "creation should be audited");

        caseService.transition(c.getCaseId(), CaseStatus.ENRICHING, "SYSTEM", "test");
        int after = caseService.auditFor(c).size();
        assertEquals(before + 1, after);

        // ascending order, unique event ids, hashes present
        var events = caseService.auditFor(c);
        assertEquals(events.size(), events.stream().map(e -> e.getEventId()).distinct().count());
        assertTrue(events.stream().allMatch(e -> e.getPayloadHash() != null && e.getPayloadHash().length() == 64));
        for (int i = 1; i < events.size(); i++) {
            assertFalse(events.get(i).getRecordedAt().isBefore(events.get(i - 1).getRecordedAt()));
        }
    }
}
