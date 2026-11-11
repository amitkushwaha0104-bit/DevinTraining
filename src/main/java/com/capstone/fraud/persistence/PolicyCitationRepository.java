package com.capstone.fraud.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PolicyCitationRepository extends JpaRepository<PolicyCitationEntity, UUID> {
    List<PolicyCitationEntity> findByRecommendationId(UUID recommendationId);
}
