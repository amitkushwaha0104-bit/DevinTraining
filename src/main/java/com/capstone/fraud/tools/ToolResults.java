package com.capstone.fraud.tools;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Structured results returned by the allow-listed evidence tools. Every
 * result carries a toolResultId and observedAt for provenance.
 */
public final class ToolResults {

    private ToolResults() {
    }

    public record TransactionDetails(
            String toolResultId,
            Instant observedAt,
            boolean found,
            String status,
            Instant timestamp,
            BigDecimal amount,
            String channel,
            String authOutcome,
            String maskedMerchant,
            boolean duplicateFingerprintMatch,
            List<String> observations) {
    }

    public record DeviceSignals(
            String toolResultId,
            Instant observedAt,
            boolean deviceKnown,
            Instant firstSeenAt,
            String trustLevel,
            List<String> observations) {
    }

    public record LocationComparison(
            String toolResultId,
            Instant observedAt,
            double distanceKm,
            long timeDeltaMinutes,
            String anomalyLevel,
            List<String> observations) {
    }

    public record MerchantRiskProfile(
            String toolResultId,
            Instant observedAt,
            String riskBand,
            boolean blocklistMatch,
            String category,
            List<String> observations) {
    }

    public record PriorDisputes(
            String toolResultId,
            Instant observedAt,
            int count,
            int similarCount,
            int lookbackDays,
            List<String> observations) {
    }

    public record CaseSla(
            String toolResultId,
            Instant observedAt,
            String queue,
            String priority,
            Instant targetAt,
            String explanation) {
    }

    public record PolicyEvidence(
            String toolResultId,
            Instant observedAt,
            boolean abstained,
            List<Passage> passages) {

        public record Passage(
                String documentId,
                String version,
                String section,
                String chunkId,
                double score,
                String passage) {
        }
    }

    public record AuditEventResult(
            String eventId,
            String eventType,
            Instant recordedAt) {
    }
}
