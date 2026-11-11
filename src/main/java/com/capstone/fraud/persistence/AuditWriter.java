package com.capstone.fraud.persistence;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Append-only audit writer. Events are only ever inserted; nothing in
 * this layer updates or deletes them. Payloads are hashed so the audit
 * trail proves integrity without storing sensitive content.
 */
@Service
public class AuditWriter {

    private final AuditEventRepository repository;
    private final IdGenerator ids;

    public AuditWriter(AuditEventRepository repository, IdGenerator ids) {
        this.repository = repository;
        this.ids = ids;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEventEntity record(UUID caseRef, String eventType, String actorType, String actorId, String summary) {
        AuditEventEntity event = new AuditEventEntity();
        event.setId(UUID.randomUUID());
        event.setEventId(ids.eventId());
        event.setCaseRef(caseRef);
        event.setEventType(eventType);
        event.setActorType(actorType);
        event.setActorId(actorId);
        event.setRecordedAt(Instant.now());
        event.setPayloadHash(hash(eventType + "|" + (summary == null ? "" : summary)));
        event.setSummary(truncate(summary));
        return repository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEventEntity record(String caseId, String eventType, String actorType, String actorId, String summary) {
        AuditEventEntity event = new AuditEventEntity();
        event.setId(UUID.randomUUID());
        event.setEventId(ids.eventId());
        event.setCaseRef(null);
        event.setEventType(eventType);
        event.setActorType(actorType);
        event.setActorId(actorId);
        event.setRecordedAt(Instant.now());
        event.setPayloadHash(hash(caseId + "|" + eventType + "|" + (summary == null ? "" : summary)));
        event.setSummary(truncate(caseId + " :: " + summary));
        return repository.save(event);
    }

    private static String hash(String payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String truncate(String summary) {
        if (summary == null) {
            return "";
        }
        return summary.length() > 1800 ? summary.substring(0, 1800) : summary;
    }
}
