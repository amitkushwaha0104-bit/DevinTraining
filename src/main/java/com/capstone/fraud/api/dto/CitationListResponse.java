package com.capstone.fraud.api.dto;

import java.util.List;

public record CitationListResponse(String caseId, List<RecommendationResponse.Citation> citations) {
}
