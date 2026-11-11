package com.capstone.fraud.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecommendationRepository extends JpaRepository<RecommendationEntity, UUID> {
    List<RecommendationEntity> findByFraudCaseIdOrderByCreatedAtDesc(UUID caseId);

    default Optional<RecommendationEntity> findLatest(UUID caseId) {
        return findByFraudCaseIdOrderByCreatedAtDesc(caseId).stream().findFirst();
    }
}
