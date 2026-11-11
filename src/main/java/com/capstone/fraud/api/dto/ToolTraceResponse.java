package com.capstone.fraud.api.dto;

import java.time.Instant;
import java.util.List;

public record ToolTraceResponse(String caseId, List<Item> traces) {

    public record Item(
            String traceId,
            String toolName,
            String requestHash,
            String status,
            long durationMs,
            String resultSummary,
            Instant executedAt) {
    }
}
