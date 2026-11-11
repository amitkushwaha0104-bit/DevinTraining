package com.capstone.fraud.persistence;

import com.capstone.fraud.domain.CaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FraudCaseRepository extends JpaRepository<FraudCaseEntity, UUID> {
    Optional<FraudCaseEntity> findByCaseId(String caseId);
    List<FraudCaseEntity> findAllByOrderByCreatedAtDesc();
    List<FraudCaseEntity> findByStatusOrderByCreatedAtDesc(CaseStatus status);
}
