package com.capstone.fraud.application;

import com.capstone.fraud.api.MaskingRules;
import com.capstone.fraud.api.dto.CaseIntakeRequest;
import com.capstone.fraud.api.dto.ReviewRequest;
import com.capstone.fraud.domain.*;
import com.capstone.fraud.persistence.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Case lifecycle service: validated intake, explicit state transitions
 * and human-review disposition recording. State transitions live here,
 * never in model output.
 */
@Service
public class FraudCaseService {

    private final FraudCaseRepository caseRepository;
    private final TransactionClaimRepository claimRepository;
    private final EvidenceItemRepository evidenceRepository;
    private final ToolTraceRepository traceRepository;
    private final RecommendationRepository recommendationRepository;
    private final PolicyCitationRepository citationRepository;
    private final ReviewDispositionRepository reviewRepository;
    private final AuditWriter auditWriter;
    private final IdGenerator ids;
    private final ObjectMapper objectMapper;

    private final AuditEventRepository auditRepository;
    private final com.capstone.fraud.knowledge.PolicyIngestionService policyIngestion;

    public FraudCaseService(FraudCaseRepository caseRepository,
                            TransactionClaimRepository claimRepository,
                            EvidenceItemRepository evidenceRepository,
                            ToolTraceRepository traceRepository,
                            RecommendationRepository recommendationRepository,
                            PolicyCitationRepository citationRepository,
                            ReviewDispositionRepository reviewRepository,
                            AuditEventRepository auditRepository,
                            AuditWriter auditWriter,
                            IdGenerator ids,
                            ObjectMapper objectMapper,
                            com.capstone.fraud.knowledge.PolicyIngestionService policyIngestion) {
        this.caseRepository = caseRepository;
        this.claimRepository = claimRepository;
        this.evidenceRepository = evidenceRepository;
        this.traceRepository = traceRepository;
        this.recommendationRepository = recommendationRepository;
        this.citationRepository = citationRepository;
        this.reviewRepository = reviewRepository;
        this.auditRepository = auditRepository;
        this.auditWriter = auditWriter;
        this.ids = ids;
        this.objectMapper = objectMapper;
        this.policyIngestion = policyIngestion;
    }

    @Transactional
    public FraudCaseEntity createCase(CaseIntakeRequest request, String analystId) {
        validateIntake(request);

        FraudCaseEntity entity = new FraudCaseEntity();
        entity.setId(UUID.randomUUID());
        entity.setCaseId(ids.caseId());
        entity.setMaskedCustomerRef(request.maskedCustomerRef());
        entity.setHomeRegion(request.homeRegion());
        entity.setChannel(parseEnum(IntakeChannel.class, request.intakeChannel(), IntakeChannel.WEB));
        entity.setAnalystId(analystId);
        entity.setReportedAt(request.reportedAt() == null ? Instant.now() : request.reportedAt());
        entity.setStatus(CaseStatus.NEW);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        caseRepository.save(entity);

        TransactionClaimEntity claim = new TransactionClaimEntity();
        claim.setId(UUID.randomUUID());
        claim.setFraudCase(entity);
        claim.setTransactionRef(request.transactionRef());
        claim.setAmount(request.amount());
        claim.setCurrency(request.currency());
        claim.setOccurredAt(request.occurredAt());
        claim.setTransactionStatus(parseEnum(TransactionStatus.class, request.status(), null));
        claim.setTransactionChannel(parseEnum(TransactionChannel.class, request.channel(), null));
        claim.setMaskedMerchant(request.maskedMerchant());
        claim.setIssueType(parseEnum(IssueType.class, request.issueType(), IssueType.OTHER));
        claim.setCustomerStatement(request.customerStatement());
        claim.setCardPossession(Boolean.TRUE.equals(request.cardPossession()));
        claim.setMerchantRecognised(Boolean.TRUE.equals(request.merchantRecognised()));
        claimRepository.save(claim);

        // The customer statement is intake evidence, kept distinct from tool-observed evidence.
        EvidenceItemEntity statement = new EvidenceItemEntity();
        statement.setId(UUID.randomUUID());
        statement.setEvidenceId(ids.evidenceId());
        statement.setFraudCase(entity);
        statement.setType(EvidenceType.CUSTOMER_STATEMENT);
        statement.setSeverity(Severity.INFO);
        statement.setValueSummary(request.customerStatement());
        statement.setProvenance(Provenance.USER_INTAKE);
        statement.setObservedAt(Instant.now());
        evidenceRepository.save(statement);

        auditWriter.record(entity.getId(), "CASE_CREATED", "USER", analystId,
                "case %s created; issue=%s".formatted(entity.getCaseId(), claim.getIssueType()));
        return entity;
    }

    private void validateIntake(CaseIntakeRequest request) {
        if (MaskingRules.containsUnmaskedIdentifier(request.maskedCustomerRef())
                || MaskingRules.containsUnmaskedIdentifier(request.transactionRef())
                || MaskingRules.containsUnmaskedIdentifier(request.maskedMerchant())) {
            throw new IntakeValidationException("references must be masked; raw card or account numbers are not accepted");
        }
        if (MaskingRules.containsMarkup(request.customerStatement())
                || MaskingRules.containsMarkup(request.maskedMerchant())) {
            throw new IntakeValidationException("markup or scripts are not permitted in text fields");
        }
        // enums validated here so API and UI share the same rules
        parseEnum(TransactionStatus.class, request.status(), null);
        parseEnum(TransactionChannel.class, request.channel(), null);
        parseEnum(IssueType.class, request.issueType(), IssueType.OTHER);
        if (!Boolean.TRUE.equals(request.syntheticDataConfirmed()) || !Boolean.TRUE.equals(request.reviewRequired())) {
            throw new IntakeValidationException("syntheticDataConfirmed and reviewRequired must both be true");
        }
    }

    @Transactional
    public FraudCaseEntity transition(String caseId, CaseStatus target, String actorType, String actorId) {
        FraudCaseEntity entity = requireCase(caseId);
        CaseStatus from = entity.getStatus();
        if (!from.canTransitionTo(target)) {
            throw new InvalidCaseStateException(caseId, from, target);
        }
        entity.setStatus(target);
        entity.setUpdatedAt(Instant.now());
        caseRepository.save(entity);
        auditWriter.record(entity.getId(), "STATE_TRANSITION", actorType, actorId,
                "%s -> %s".formatted(from, target));
        return entity;
    }

    public FraudCaseEntity requireCase(String caseId) {
        return caseRepository.findByCaseId(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));
    }

    public List<FraudCaseEntity> listCases() {
        return caseRepository.findAllByOrderByCreatedAtDesc();
    }

    public TransactionClaimEntity claimFor(FraudCaseEntity entity) {
        return claimRepository.findByFraudCaseId(entity.getId())
                .orElseThrow(() -> new CaseNotFoundException("claim for " + entity.getCaseId()));
    }

    public List<EvidenceItemEntity> evidenceFor(FraudCaseEntity entity) {
        return evidenceRepository.findByFraudCaseIdOrderByObservedAtAsc(entity.getId());
    }

    public List<ToolTraceEntity> tracesFor(FraudCaseEntity entity) {
        return traceRepository.findByFraudCaseIdOrderByExecutedAtAsc(entity.getId());
    }

    public RecommendationEntity latestRecommendation(FraudCaseEntity entity) {
        return recommendationRepository.findLatest(entity.getId()).orElse(null);
    }

    public List<PolicyCitationEntity> citationsFor(RecommendationEntity recommendation) {
        return recommendation == null ? List.of() : citationRepository.findByRecommendationId(recommendation.getId());
    }

    public List<ReviewDispositionEntity> reviewsFor(FraudCaseEntity entity) {
        return reviewRepository.findByFraudCaseIdOrderByRecordedAtDesc(entity.getId());
    }

    public List<AuditEventEntity> auditFor(FraudCaseEntity entity) {
        return auditRepository.findByCaseRefOrderByRecordedAtAsc(entity.getId());
    }

    @Transactional
    public EvidenceItemEntity addEvidence(FraudCaseEntity entity, EvidenceType type, Severity severity,
                                          String summary, Provenance provenance, String detailJson) {
        EvidenceItemEntity item = new EvidenceItemEntity();
        item.setId(UUID.randomUUID());
        item.setEvidenceId(ids.evidenceId());
        item.setFraudCase(entity);
        item.setType(type);
        item.setSeverity(severity);
        item.setValueSummary(summary);
        item.setProvenance(provenance);
        item.setObservedAt(Instant.now());
        item.setDetailJson(detailJson);
        return evidenceRepository.save(item);
    }

    @Transactional
    public RecommendationEntity persistRecommendation(FraudCaseEntity entity,
                                                      com.capstone.fraud.agent.common.ValidatedRecommendation rec,
                                                      ProcessorMode mode) {
        RecommendationEntity r = new RecommendationEntity();
        r.setId(UUID.randomUUID());
        r.setRecommendationId(ids.recommendationId());
        r.setFraudCase(entity);
        r.setMode(mode);
        r.setClassification(rec.classification());
        r.setRecommendedRoute(rec.recommendedRoute());
        r.setRiskLevel(rec.riskLevel());
        r.setConfidence(rec.confidence());
        r.setHumanReviewRequired(rec.humanReviewRequired());
        r.setRationale(rec.rationale());
        r.setMissingInformationJson(toJson(rec.missingInformation()));
        r.setEvidenceIdsJson(toJson(rec.evidenceIds()));
        r.setToolTraceIdsJson(toJson(rec.toolTraceIds()));
        r.setRuleIdsJson(toJson(rec.ruleIds()));
        r.setCreatedAt(Instant.now());
        recommendationRepository.save(r);

        for (var citation : rec.policyCitations()) {
            PolicyCitationEntity c = new PolicyCitationEntity();
            c.setId(UUID.randomUUID());
            c.setRecommendation(r);
            c.setDocumentId(citation.documentId());
            c.setVersion(citation.version());
            c.setSection(citation.section());
            c.setChunkId(citation.chunkId());
            c.setScore(citation.score());
            var passage = policyIngestion.passageByChunkId(citation.chunkId());
            c.setPassage(passage == null ? "" : passage.passage());
            citationRepository.save(c);
        }
        return r;
    }

    @Transactional
    public ReviewDispositionEntity recordReview(String caseId, ReviewRequest request, String reviewerId) {
        FraudCaseEntity entity = requireCase(caseId);
        ReviewAction action = parseEnum(ReviewAction.class, request.action(), null);

        if (entity.getStatus() == CaseStatus.READY_FOR_REVIEW) {
            transition(caseId, CaseStatus.UNDER_REVIEW, "USER", reviewerId);
        } else if (entity.getStatus() != CaseStatus.UNDER_REVIEW) {
            throw new InvalidCaseStateException(caseId, entity.getStatus(), CaseStatus.UNDER_REVIEW);
        }

        ReviewDispositionEntity review = new ReviewDispositionEntity();
        review.setId(UUID.randomUUID());
        review.setReviewId(ids.reviewId());
        review.setFraudCase(entity);
        review.setReviewerId(reviewerId);
        review.setAction(action);
        review.setReason(request.reason());
        review.setRecordedAt(Instant.now());

        CaseStatus target = switch (action) {
            case ACCEPT_AND_ROUTE, ESCALATE -> {
                review.setSelectedRoute(resolveRoute(entity, request, action));
                yield CaseStatus.ROUTED;
            }
            case REROUTE -> {
                if (request.selectedRoute() == null || request.selectedRoute().isBlank()) {
                    throw new IntakeValidationException("selectedRoute is required when rerouting");
                }
                parseEnum(RecommendedRoute.class, request.selectedRoute(), null);
                review.setSelectedRoute(request.selectedRoute());
                yield CaseStatus.ROUTED;
            }
            case REQUEST_INFORMATION -> CaseStatus.INCOMPLETE;
            case CLOSE_CASE -> CaseStatus.CLOSED;
        };
        reviewRepository.save(review);

        entity = transition(caseId, target, "USER", reviewerId);
        auditWriter.record(entity.getId(), "REVIEW_DISPOSITION", "USER", reviewerId,
                "action=%s route=%s".formatted(action, review.getSelectedRoute()));
        return review;
    }

    private String resolveRoute(FraudCaseEntity entity, ReviewRequest request, ReviewAction action) {
        RecommendationEntity latest = latestRecommendation(entity);
        if (action == ReviewAction.ESCALATE) {
            return RecommendedRoute.FRAUD_INVESTIGATION.name();
        }
        return latest != null ? latest.getRecommendedRoute().name()
                : (request.selectedRoute() != null ? request.selectedRoute() : RecommendedRoute.MANUAL_REVIEW.name());
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            if (fallback != null) {
                return fallback;
            }
            throw new IntakeValidationException("missing value for " + type.getSimpleName());
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException e) {
            if (fallback != null) {
                return fallback;
            }
            throw new IntakeValidationException("unknown " + type.getSimpleName() + " value: " + value);
        }
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    public List<String> jsonToList(String json) {
        try {
            return objectMapper.readValue(json == null ? "[]" : json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }
}
