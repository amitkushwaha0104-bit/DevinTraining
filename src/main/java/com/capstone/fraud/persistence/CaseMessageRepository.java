package com.capstone.fraud.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CaseMessageRepository extends JpaRepository<CaseMessageEntity, UUID> {
    List<CaseMessageEntity> findByCaseIdOrderByCreatedAtAsc(String caseId);

    @Query("select distinct m.caseId from CaseMessageEntity m")
    List<String> findDistinctCaseIds();

    @Modifying
    void deleteByCaseId(String caseId);
}
