package com.capstone.fraud.agent.common;

import java.time.Instant;

public record EvidenceSnapshot(
        String evidenceId,
        String type,
        String severity,
        String valueSummary,
        String provenance,
        Instant observedAt) {
}
