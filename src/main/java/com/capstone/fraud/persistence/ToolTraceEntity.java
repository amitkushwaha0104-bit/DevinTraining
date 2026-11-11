package com.capstone.fraud.persistence;

import com.capstone.fraud.domain.ToolStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tool_trace", indexes = {
        @Index(name = "idx_trace_case", columnList = "case_id")
})
@Getter
@Setter
public class ToolTraceEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String traceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private FraudCaseEntity fraudCase;

    @Column(nullable = false, length = 64)
    private String toolName;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ToolStatus status;

    @Column(nullable = false)
    private long durationMs;

    @Column(nullable = false, length = 2000)
    private String resultSummary;

    @Column(nullable = false)
    private Instant executedAt;
}
