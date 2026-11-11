package com.capstone.fraud.api.dto;

import java.time.Instant;

public record ReviewResponse(
        String reviewId,
        String caseId,
        String reviewerId,
        String action,
        String selectedRoute,
        String reason,
        Instant recordedAt,
        String caseStatus) {
}
