package com.capstone.fraud.persistence;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Human-readable business identifiers for persisted artefacts.
 */
@Component
public class IdGenerator {

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy").withZone(ZoneOffset.UTC);
    private final AtomicLong sequence = new AtomicLong(100);

    public String caseId() {
        return "CASE-" + YEAR.format(Instant.now()) + "-" + String.format("%04d", next());
    }

    public String evidenceId() {
        return "EV-" + next();
    }

    public String traceId() {
        return "TR-" + next();
    }

    public String recommendationId() {
        return "REC-" + next();
    }

    public String reviewId() {
        return "REV-" + next();
    }

    public String eventId() {
        return "AE-" + next();
    }

    private long next() {
        return sequence.getAndIncrement();
    }
}
