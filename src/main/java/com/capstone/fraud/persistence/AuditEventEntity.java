package com.capstone.fraud.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_event", indexes = {
        @Index(name = "idx_audit_case", columnList = "case_id"),
        @Index(name = "idx_audit_recorded", columnList = "recordedAt")
})
@Getter
@Setter
public class AuditEventEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String eventId;

    @Column(name = "case_id")
    private UUID caseRef;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 24)
    private String actorType;

    @Column(length = 64)
    private String actorId;

    @Column(nullable = false)
    private Instant recordedAt;

    @Column(nullable = false, length = 64)
    private String payloadHash;

    @Column(length = 2000)
    private String summary;
}
