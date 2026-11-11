package com.capstone.fraud.persistence;

import com.capstone.fraud.domain.Classification;
import com.capstone.fraud.domain.ProcessorMode;
import com.capstone.fraud.domain.RecommendedRoute;
import com.capstone.fraud.domain.RiskLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recommendation", indexes = {
        @Index(name = "idx_recommendation_case", columnList = "case_id")
})
@Getter
@Setter
public class RecommendationEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String recommendationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private FraudCaseEntity fraudCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProcessorMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Classification classification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RecommendedRoute recommendedRoute;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RiskLevel riskLevel;

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false)
    private boolean humanReviewRequired;

    @Column(nullable = false, length = 4000)
    private String rationale;

    @Column(length = 4000)
    private String missingInformationJson;

    @Column(length = 2000)
    private String evidenceIdsJson;

    @Column(length = 2000)
    private String toolTraceIdsJson;

    @Column(length = 1000)
    private String ruleIdsJson;

    @Column(nullable = false)
    private Instant createdAt;
}
