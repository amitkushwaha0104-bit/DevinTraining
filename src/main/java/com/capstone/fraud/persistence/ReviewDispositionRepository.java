package com.capstone.fraud.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReviewDispositionRepository extends JpaRepository<ReviewDispositionEntity, UUID> {
    List<ReviewDispositionEntity> findByFraudCaseIdOrderByRecordedAtDesc(UUID caseId);
}
