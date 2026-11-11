package com.capstone.fraud.api.dto;

import java.time.Instant;
import java.util.List;

public record EvidenceResponse(String caseId, List<Item> evidence) {

    public record Item(
            String evidenceId,
            String type,
            String severity,
            String valueSummary,
            String provenance,
            Instant observedAt) {
    }
}
