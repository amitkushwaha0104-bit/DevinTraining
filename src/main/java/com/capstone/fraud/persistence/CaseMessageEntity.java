package com.capstone.fraud.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "case_message", indexes = {
        @Index(name = "idx_message_case", columnList = "case_id")
})
@Getter
@Setter
public class CaseMessageEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String caseId;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, length = 8000)
    private String content;

    @Column(nullable = false)
    private Instant createdAt;
}
