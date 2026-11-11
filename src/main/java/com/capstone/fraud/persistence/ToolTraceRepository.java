package com.capstone.fraud.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ToolTraceRepository extends JpaRepository<ToolTraceEntity, UUID> {
    List<ToolTraceEntity> findByFraudCaseIdOrderByExecutedAtAsc(UUID caseId);
}
