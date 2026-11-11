package com.capstone.fraud.persistence;

import com.capstone.fraud.domain.ReviewAction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_disposition", indexes = {
        @Index(name = "idx_review_case", columnList = "case_id")
})
@Getter
@Setter
public class ReviewDispositionEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String reviewId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private FraudCaseEntity fraudCase;

    @Column(nullable = false, length = 64)
    private String reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReviewAction action;

    @Column(length = 32)
    private String selectedRoute;

    @Column(nullable = false, length = 4000)
    private String reason;

    @Column(nullable = false)
    private Instant recordedAt;
}
