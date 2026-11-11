package com.capstone.fraud.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvidenceItemRepository extends JpaRepository<EvidenceItemEntity, UUID> {
    List<EvidenceItemEntity> findByFraudCaseIdOrderByObservedAtAsc(UUID caseId);
}
