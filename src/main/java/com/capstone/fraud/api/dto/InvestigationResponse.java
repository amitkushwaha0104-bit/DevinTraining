package com.capstone.fraud.api.dto;

import java.util.List;

public record InvestigationResponse(
        String caseId,
        String status,
        String mode,
        List<String> plan,
        RecommendationResponse recommendation) {
}
