package com.capstone.fraud.knowledge;

/**
 * A retrieved policy chunk with exact citation metadata.
 */
public record PolicyPassage(
        String documentId,
        String title,
        String version,
        String section,
        String chunkId,
        String effectiveDate,
        double score,
        String passage) {
}
