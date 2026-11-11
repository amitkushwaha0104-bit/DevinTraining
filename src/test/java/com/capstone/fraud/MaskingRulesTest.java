package com.capstone.fraud;

import com.capstone.fraud.api.MaskingRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskingRulesTest {

    @Test
    void rejectsRawCardNumbers() {
        assertTrue(MaskingRules.containsUnmaskedIdentifier("4111111111111111"));
        assertTrue(MaskingRules.containsUnmaskedIdentifier("card 4111111111111111 here"));
    }

    @Test
    void acceptsMaskedReferences() {
        assertFalse(MaskingRules.containsUnmaskedIdentifier("CUST-••••-4821"));
        assertFalse(MaskingRules.containsUnmaskedIdentifier("NORTHSTAR DIGITAL ••••73"));
        assertFalse(MaskingRules.containsUnmaskedIdentifier("TXN-2026-784102"));
    }

    @Test
    void rejectsMarkup() {
        assertTrue(MaskingRules.containsMarkup("<script>alert(1)</script>"));
        assertTrue(MaskingRules.containsMarkup("a < b"));
        assertFalse(MaskingRules.containsMarkup("I do not recognise this purchase"));
    }
}
