package com.capstone.fraud;

import com.capstone.fraud.tools.DeterministicFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeterministicFixturesTest {

    private final DeterministicFixtures fixtures = new DeterministicFixtures("fraud-training-v1");

    @Test
    void identicalInputProducesIdenticalSequence() {
        var r1 = fixtures.randomFor("device", "CUST-1");
        var r2 = fixtures.randomFor("device", "CUST-1");
        for (int i = 0; i < 10; i++) {
            assertEquals(r1.nextLong(), r2.nextLong());
        }
    }

    @Test
    void requestHashIsStableAndShort() {
        assertEquals(fixtures.requestHash("a", "b"), fixtures.requestHash("a", "b"));
        assertEquals(16, fixtures.requestHash("a", "b").length());
    }

    @Test
    void namedScenarioRefsMapToProfiles() {
        assertEquals(DeterministicFixtures.Scenario.UNAUTHORISED, fixtures.scenarioFor("TXN-2026-784102"));
        assertEquals(DeterministicFixtures.Scenario.DUPLICATE, fixtures.scenarioFor("TXN-2026-DUP01"));
        assertEquals(DeterministicFixtures.Scenario.MERCHANT_RECOGNISED, fixtures.scenarioFor("TXN-2026-MRC01"));
        assertEquals(DeterministicFixtures.Scenario.MISSING, fixtures.scenarioFor("TXN-2026-MISSING"));
        assertEquals(DeterministicFixtures.Scenario.HASHED, fixtures.scenarioFor("TXN-2026-0001"));
    }
}
