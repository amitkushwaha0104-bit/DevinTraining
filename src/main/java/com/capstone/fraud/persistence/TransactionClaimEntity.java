package com.capstone.fraud.persistence;

import com.capstone.fraud.domain.IssueType;
import com.capstone.fraud.domain.TransactionChannel;
import com.capstone.fraud.domain.TransactionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction_claim", indexes = {
        @Index(name = "idx_claim_case", columnList = "case_id")
})
@Getter
@Setter
public class TransactionClaimEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private FraudCaseEntity fraudCase;

    @Column(nullable = false, length = 64)
    private String transactionRef;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionStatus transactionStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TransactionChannel transactionChannel;

    @Column(nullable = false, length = 96)
    private String maskedMerchant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IssueType issueType;

    @Column(nullable = false, length = 4000)
    private String customerStatement;

    @Column(nullable = false)
    private boolean cardPossession;

    @Column(nullable = false)
    private boolean merchantRecognised;
}
