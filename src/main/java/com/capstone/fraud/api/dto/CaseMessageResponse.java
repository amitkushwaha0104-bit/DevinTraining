package com.capstone.fraud.api.dto;

import java.util.List;

public record CaseMessageResponse(
        String caseId,
        String answer,
        List<RecommendationResponse.Citation> citations) {
}
