package com.capstone.fraud.persistence;

import com.capstone.fraud.domain.CaseStatus;
import com.capstone.fraud.domain.IntakeChannel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_case", indexes = {
        @Index(name = "idx_fraud_case_status", columnList = "status")
})
@Getter
@Setter
public class FraudCaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String caseId;

    @Column(nullable = false, length = 64)
    private String maskedCustomerRef;

    @Column(length = 64)
    private String homeRegion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IntakeChannel channel;

    @Column(nullable = false, length = 64)
    private String analystId;

    @Column(nullable = false)
    private Instant reportedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CaseStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;
}
