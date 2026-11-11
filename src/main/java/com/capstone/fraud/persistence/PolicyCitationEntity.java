package com.capstone.fraud.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "policy_citation", indexes = {
        @Index(name = "idx_citation_recommendation", columnList = "recommendation_id")
})
@Getter
@Setter
public class PolicyCitationEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private RecommendationEntity recommendation;

    @Column(nullable = false, length = 64)
    private String documentId;

    @Column(nullable = false, length = 16)
    private String version;

    @Column(nullable = false, length = 64)
    private String section;

    @Column(nullable = false, length = 64)
    private String chunkId;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false, length = 4000)
    private String passage;
}
