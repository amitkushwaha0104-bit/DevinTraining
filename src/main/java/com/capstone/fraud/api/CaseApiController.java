package com.capstone.fraud.api;

import com.capstone.fraud.api.dto.*;
import com.capstone.fraud.application.CaseQaService;
import com.capstone.fraud.application.FraudCaseService;
import com.capstone.fraud.application.InvestigationOrchestrator;
import com.capstone.fraud.persistence.*;
import com.capstone.fraud.domain.ProcessorMode;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Fraud case REST API. All endpoints require authentication; mutating
 * endpoints are additionally restricted by role in SecurityConfig.
 */
@RestController
@RequestMapping("/api/cases")
public class CaseApiController {

    private final FraudCaseService caseService;
    private final InvestigationOrchestrator orchestrator;
    private final CaseQaService qaService;

    public CaseApiController(FraudCaseService caseService,
                             InvestigationOrchestrator orchestrator,
                             CaseQaService qaService) {
        this.caseService = caseService;
        this.orchestrator = orchestrator;
        this.qaService = qaService;
    }

    @PostMapping
    public ResponseEntity<CaseSummaryResponse> create(@Valid @RequestBody CaseIntakeRequest request,
                                                      Authentication auth) {
        FraudCaseEntity entity = caseService.createCase(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(entity));
    }

    @GetMapping("/{caseId}")
    public CaseSummaryResponse get(@PathVariable String caseId) {
        return toSummary(caseService.requireCase(caseId));
    }

    @PostMapping("/{caseId}/investigations")
    public InvestigationResponse investigate(@PathVariable String caseId, Authentication auth) {
        var result = orchestrator.investigate(caseId, auth.getName());
        return new InvestigationResponse(result.caseId(), result.status().name(),
                result.mode().name(), result.plan(), toRecommendation(result.recommendation()));
    }

    @GetMapping("/{caseId}/evidence")
    public EvidenceResponse evidence(@PathVariable String caseId) {
        FraudCaseEntity entity = caseService.requireCase(caseId);
        List<EvidenceResponse.Item> items = caseService.evidenceFor(entity).stream()
                .map(e -> new EvidenceResponse.Item(e.getEvidenceId(), e.getType().name(),
                        e.getSeverity().name(), e.getValueSummary(), e.getProvenance().name(), e.getObservedAt()))
                .toList();
        return new EvidenceResponse(caseId, items);
    }

    @GetMapping("/{caseId}/tool-traces")
    public ToolTraceResponse toolTraces(@PathVariable String caseId) {
        FraudCaseEntity entity = caseService.requireCase(caseId);
        List<ToolTraceResponse.Item> items = caseService.tracesFor(entity).stream()
                .map(t -> new ToolTraceResponse.Item(t.getTraceId(), t.getToolName(), t.getRequestHash(),
                        t.getStatus().name(), t.getDurationMs(), t.getResultSummary(), t.getExecutedAt()))
                .toList();
        return new ToolTraceResponse(caseId, items);
    }

    @GetMapping("/{caseId}/recommendation")
    public ResponseEntity<RecommendationResponse> recommendation(@PathVariable String caseId) {
        RecommendationEntity rec = caseService.latestRecommendation(caseService.requireCase(caseId));
        if (rec == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toRecommendation(rec));
    }

    @GetMapping("/{caseId}/citations")
    public CitationListResponse citations(@PathVariable String caseId) {
        RecommendationEntity rec = caseService.latestRecommendation(caseService.requireCase(caseId));
        List<RecommendationResponse.Citation> citations = rec == null ? List.of()
                : caseService.citationsFor(rec).stream().map(this::toCitation).toList();
        return new CitationListResponse(caseId, citations);
    }

    @PostMapping("/{caseId}/messages")
    public CaseMessageResponse message(@PathVariable String caseId,
                                       @Valid @RequestBody CaseMessageRequest request,
                                       Authentication auth) {
        return qaService.ask(caseId, request.message(), auth.getName());
    }

    @PostMapping("/{caseId}/reviews")
    public ReviewResponse review(@PathVariable String caseId,
                                 @Valid @RequestBody ReviewRequest request,
                                 Authentication auth) {
        ReviewDispositionEntity review = caseService.recordReview(caseId, request, auth.getName());
        FraudCaseEntity entity = caseService.requireCase(caseId);
        return new ReviewResponse(review.getReviewId(), caseId, review.getReviewerId(),
                review.getAction().name(), review.getSelectedRoute(), review.getReason(),
                review.getRecordedAt(), entity.getStatus().name());
    }

    private CaseSummaryResponse toSummary(FraudCaseEntity e) {
        TransactionClaimEntity claim = caseService.claimFor(e);
        return new CaseSummaryResponse(e.getCaseId(), e.getMaskedCustomerRef(), e.getStatus().name(),
                e.getChannel().name(), e.getAnalystId(), e.getReportedAt(), e.getCreatedAt(),
                new CaseSummaryResponse.ClaimView(claim.getTransactionRef(), claim.getAmount().toPlainString(),
                        claim.getCurrency(), claim.getOccurredAt(), claim.getTransactionStatus().name(),
                        claim.getTransactionChannel().name(), claim.getMaskedMerchant(), claim.getIssueType().name(),
                        claim.getCustomerStatement(), claim.isCardPossession(), claim.isMerchantRecognised()));
    }

    private RecommendationResponse toRecommendation(RecommendationEntity r) {
        if (r == null) {
            return null;
        }
        return new RecommendationResponse(r.getFraudCase().getCaseId(), r.getRecommendationId(), r.getMode().name(),
                r.getClassification().name(), r.getRiskLevel().name(), r.getRecommendedRoute().name(),
                r.getConfidence(), r.isHumanReviewRequired(),
                caseService.jsonToList(r.getEvidenceIdsJson()),
                caseService.citationsFor(r).stream().map(this::toCitation).toList(),
                caseService.jsonToList(r.getMissingInformationJson()),
                caseService.jsonToList(r.getRuleIdsJson()),
                caseService.jsonToList(r.getToolTraceIdsJson()),
                r.getRationale(), r.getCreatedAt());
    }

    private RecommendationResponse.Citation toCitation(PolicyCitationEntity c) {
        return new RecommendationResponse.Citation(c.getDocumentId(), c.getVersion(), c.getSection(),
                c.getChunkId(), c.getScore(), c.getPassage());
    }
}
