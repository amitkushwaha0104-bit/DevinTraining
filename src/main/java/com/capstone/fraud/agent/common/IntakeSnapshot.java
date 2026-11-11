package com.capstone.fraud.agent.common;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable, validated snapshot of intake facts for a case. Masked
 * references only - never raw card or account numbers.
 */
public record IntakeSnapshot(
        String maskedCustomerRef,
        String homeRegion,
        String transactionRef,
        BigDecimal amount,
        String currency,
        Instant occurredAt,
        String transactionStatus,
        String transactionChannel,
        String maskedMerchant,
        String issueType,
        String customerStatement,
        boolean cardPossession,
        boolean merchantRecognised) {
}
