package com.capstone.fraud.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionClaimRepository extends JpaRepository<TransactionClaimEntity, UUID> {
    Optional<TransactionClaimEntity> findByFraudCaseId(UUID caseId);
}
