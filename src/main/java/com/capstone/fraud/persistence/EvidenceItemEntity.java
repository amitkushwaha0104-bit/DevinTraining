package com.capstone.fraud.persistence;

import com.capstone.fraud.domain.EvidenceType;
import com.capstone.fraud.domain.Provenance;
import com.capstone.fraud.domain.Severity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evidence_item", indexes = {
        @Index(name = "idx_evidence_case", columnList = "case_id")
})
@Getter
@Setter
public class EvidenceItemEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String evidenceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private FraudCaseEntity fraudCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EvidenceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity;

    @Column(nullable = false, length = 2000)
    private String valueSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Provenance provenance;

    @Column(nullable = false)
    private Instant observedAt;

    @Column(length = 4000)
    private String detailJson;
}
