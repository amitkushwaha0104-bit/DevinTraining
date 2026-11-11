package com.capstone.fraud.agent.common;

public record ToolTraceSnapshot(
        String traceId,
        String toolName,
        String status,
        long durationMs,
        String resultSummary) {
}
