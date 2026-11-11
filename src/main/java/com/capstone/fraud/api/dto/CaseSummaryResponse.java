package com.capstone.fraud.api.dto;

import java.time.Instant;

public record CaseSummaryResponse(
        String caseId,
        String maskedCustomerRef,
        String status,
        String channel,
        String analystId,
        Instant reportedAt,
        Instant createdAt,
        ClaimView claim) {

    public record ClaimView(
            String transactionRef,
            String amount,
            String currency,
            Instant occurredAt,
            String status,
            String channel,
            String maskedMerchant,
            String issueType,
            String customerStatement,
            boolean cardPossession,
            boolean merchantRecognised) {
    }
}
