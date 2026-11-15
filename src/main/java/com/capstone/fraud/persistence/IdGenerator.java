package com.capstone.fraud.persistence;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Human-readable business identifiers for persisted artefacts.
 * The shared sequence is seeded from the clock so a restart can
 * never re-issue IDs that a previous run already persisted.
 */
@Component
public class IdGenerator {

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy").withZone(ZoneOffset.UTC);
    private final AtomicLong sequence = new AtomicLong(seed());

    private static long seed() {
        // millisecond-derived base: two boots at least 1ms apart are
        // 1000 id-slots apart, so persisted IDs can never collide
        return Instant.now().toEpochMilli() % 1_000_000 * 1000;
    }

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
