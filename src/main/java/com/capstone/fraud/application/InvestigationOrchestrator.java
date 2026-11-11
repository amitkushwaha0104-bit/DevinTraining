package com.capstone.fraud.application;

import com.capstone.fraud.agent.common.*;
import com.capstone.fraud.api.dto.RecommendationResponse;
import com.capstone.fraud.config.CapstoneProperties;
import com.capstone.fraud.domain.*;
import com.capstone.fraud.knowledge.PolicyPassage;
import com.capstone.fraud.knowledge.PolicyRetriever;
import com.capstone.fraud.persistence.*;
import com.capstone.fraud.tools.EvidenceTools;
import com.capstone.fraud.tools.ToolResults.*;
import com.capstone.fraud.tools.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded plan/act/observe orchestration. Evidence collection is
 * deterministic and happens in application code; the configured
 * processor only interprets validated evidence. Any failure falls back
 * to rules mode or a safe INCOMPLETE/manual-review state - never to an
 * automated action.
 */
@Service
public class InvestigationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(InvestigationOrchestrator.class);

    public static final List<String> EVIDENCE_PLAN = List.of(
            "getTransactionDetails",
            "getDeviceSignals",
            "compareRecentLocations",
            "getMerchantRiskProfile",
            "getPriorDisputes",
            "retrievePolicyEvidence");

    private final FraudCaseService caseService;
    private final EvidenceTools tools;
    private final ToolRunner toolRunner;
    private final PolicyRetriever policyRetriever;
    private final RecommendationValidator validator;
    private final RulesFraudCaseAgentHolder rulesAgent;
    private final SpringAiAgentHolder springAiAgent;
    private final CapstoneProperties properties;
    private final AuditWriter auditWriter;

    public InvestigationOrchestrator(FraudCaseService caseService,
                                     EvidenceTools tools,
                                     ToolRunner toolRunner,
                                     PolicyRetriever policyRetriever,
                                     RecommendationValidator validator,
                                     org.springframework.context.ApplicationContext context,
                                     CapstoneProperties properties,
                                     AuditWriter auditWriter) {
        this.caseService = caseService;
        this.tools = tools;
        this.toolRunner = toolRunner;
        this.policyRetriever = policyRetriever;
        this.validator = validator;
        this.rulesAgent = new RulesFraudCaseAgentHolder(
                (com.capstone.fraud.agent.rules.RulesFraudCaseAgent) context.getBean("rulesFraudCaseAgent"));
        this.springAiAgent = new SpringAiAgentHolder(context);
        this.properties = properties;
        this.auditWriter = auditWriter;
    }

    /** Optional holder so the Spring AI adapter is only required when configured. */
    static final class SpringAiAgentHolder {
        private final FraudCaseProcessor agent;

        SpringAiAgentHolder(org.springframework.context.ApplicationContext context) {
            FraudCaseProcessor resolved = null;
            try {
                resolved = context.getBean(
                        com.capstone.fraud.agent.springai.SpringAiFraudCaseAgent.class);
            } catch (Exception e) {
                LoggerFactory.getLogger(InvestigationOrchestrator.class)
                        .info("Spring AI adapter unavailable; rules mode only");
            }
            this.agent = resolved;
        }

        FraudCaseProcessor agent() {
            return agent;
        }
    }

    record RulesFraudCaseAgentHolder(com.capstone.fraud.agent.rules.RulesFraudCaseAgent agent) {
    }

    public record InvestigationResult(String caseId, CaseStatus status, ProcessorMode mode,
                                      List<String> plan, RecommendationEntity recommendation) {
    }

    @Transactional
    public InvestigationResult investigate(String caseId, String actorId) {
        FraudCaseEntity entity = caseService.requireCase(caseId);
        if (entity.getStatus() != CaseStatus.NEW && entity.getStatus() != CaseStatus.INCOMPLETE) {
            throw new InvalidCaseStateException(caseId, entity.getStatus(), CaseStatus.ENRICHING);
        }
        caseService.transition(caseId, CaseStatus.ENRICHING, "USER", actorId);
        final FraudCaseEntity working = entity;
        TransactionClaimEntity claim = caseService.claimFor(working);

        Map<String, Object> results = new LinkedHashMap<>();
        List<EvidenceItemEntity> newEvidence = new ArrayList<>();

        // Act: bounded evidence plan through allow-listed tools.
        var txnOutcome = toolRunner.run(working, "getTransactionDetails",
                () -> tools.getTransactionDetails(claim.getTransactionRef()), claim.getTransactionRef());
        record(results, "getTransactionDetails", txnOutcome.result());
        txnOutcome.resultOptional().ifPresent(r -> newEvidence.add(evidenceFrom(working, r)));

        var deviceOutcome = toolRunner.run(working, "getDeviceSignals",
                () -> tools.getDeviceSignals(entity.getMaskedCustomerRef(), claim.getTransactionRef()),
                entity.getMaskedCustomerRef());
        record(results, "getDeviceSignals", deviceOutcome.result());
        deviceOutcome.resultOptional().ifPresent(r -> newEvidence.add(evidenceFrom(working, r)));

        var locationOutcome = toolRunner.run(working, "compareRecentLocations",
                () -> tools.compareRecentLocations(entity.getMaskedCustomerRef(), claim.getTransactionRef()),
                entity.getMaskedCustomerRef());
        record(results, "compareRecentLocations", locationOutcome.result());
        locationOutcome.resultOptional().ifPresent(r -> newEvidence.add(evidenceFrom(working, r)));

        var merchantOutcome = toolRunner.run(working, "getMerchantRiskProfile",
                () -> tools.getMerchantRiskProfile(claim.getMaskedMerchant(), claim.getTransactionRef()),
                claim.getMaskedMerchant());
        record(results, "getMerchantRiskProfile", merchantOutcome.result());
        merchantOutcome.resultOptional().ifPresent(r -> newEvidence.add(evidenceFrom(working, r)));

        var disputesOutcome = toolRunner.run(working, "getPriorDisputes",
                () -> tools.getPriorDisputes(entity.getMaskedCustomerRef(), claim.getTransactionRef()),
                entity.getMaskedCustomerRef());
        record(results, "getPriorDisputes", disputesOutcome.result());
        disputesOutcome.resultOptional().ifPresent(r -> newEvidence.add(evidenceFrom(working, r)));

        // Retrieve: policy passages with exact citation metadata.
        var policyOutcome = toolRunner.run(working, "retrievePolicyEvidence",
                () -> tools.retrievePolicyEvidence(policyQuery(entity, claim)),
                entity.getCaseId());
        record(results, "retrievePolicyEvidence", policyOutcome.result());
        List<PolicyPassage> passages = policyRetriever.retrieve(policyQuery(entity, claim));

        // Assess + recommend: processor interprets validated evidence only.
        FraudCaseContext context = buildContext(entity, claim, newEvidence, passages, results);
        ProcessorMode modeUsed = ProcessorMode.valueOf(properties.processorMode().replace('-', '_').toUpperCase());
        ValidatedRecommendation validated = analyze(context, modeUsed, entity);

        // SLA for the recommended route (advisory, still a traced tool call).
        var slaOutcome = toolRunner.run(working, "calculateCaseSla",
                () -> tools.calculateCaseSla(validated.recommendedRoute().name(), validated.riskLevel().name()),
                entity.getCaseId());
        slaOutcome.resultOptional().ifPresent(r -> newEvidence.add(evidenceFrom(working, r)));

        RecommendationEntity persisted = caseService.persistRecommendation(entity, validated, modeUsed);

        CaseStatus target = validated.classification() == Classification.INSUFFICIENT_EVIDENCE
                ? CaseStatus.INCOMPLETE : CaseStatus.READY_FOR_REVIEW;
        caseService.transition(caseId, target, "SYSTEM", "orchestrator");
        auditWriter.record(entity.getId(), "INVESTIGATION_COMPLETED", "SYSTEM", "orchestrator",
                "mode=%s classification=%s route=%s".formatted(
                        modeUsed, validated.classification(), validated.recommendedRoute()));

        return new InvestigationResult(caseId, target, modeUsed, EVIDENCE_PLAN, persisted);
    }

    private ValidatedRecommendation analyze(FraudCaseContext context, ProcessorMode mode, FraudCaseEntity entity) {
        if (mode == ProcessorMode.SPRING_AI && springAiAgent.agent() != null) {
            try {
                return validator.validate(springAiAgent.agent().analyze(context), context);
            } catch (Exception e) {
                log.warn("spring-ai processor failed for case {}, falling back to rules: {}",
                        entity.getCaseId(), e.toString());
                auditWriter.record(entity.getId(), "AI_FALLBACK", "SYSTEM", "orchestrator",
                        "spring-ai failed; rules mode used");
            }
        }
        return validator.validate(rulesAgent.agent().analyze(context), context);
    }

    private FraudCaseContext buildContext(FraudCaseEntity entity, TransactionClaimEntity claim,
                                          List<EvidenceItemEntity> newEvidence,
                                          List<PolicyPassage> passages,
                                          Map<String, Object> results) {
        var intake = new IntakeSnapshot(
                entity.getMaskedCustomerRef(), entity.getHomeRegion(),
                claim.getTransactionRef(), claim.getAmount(), claim.getCurrency(), claim.getOccurredAt(),
                claim.getTransactionStatus().name(), claim.getTransactionChannel().name(),
                claim.getMaskedMerchant(), claim.getIssueType().name(), claim.getCustomerStatement(),
                claim.isCardPossession(), claim.isMerchantRecognised());

        List<EvidenceSnapshot> evidence = caseService.evidenceFor(entity).stream()
                .map(e -> new EvidenceSnapshot(e.getEvidenceId(), e.getType().name(), e.getSeverity().name(),
                        e.getValueSummary(), e.getProvenance().name(), e.getObservedAt()))
                .toList();

        List<ToolTraceSnapshot> traces = caseService.tracesFor(entity).stream()
                .map(t -> new ToolTraceSnapshot(t.getTraceId(), t.getToolName(), t.getStatus().name(),
                        t.getDurationMs(), t.getResultSummary()))
                .toList();

        return new FraudCaseContext(entity.getCaseId(), intake, evidence, passages, traces, "", results);
    }

    private String policyQuery(FraudCaseEntity entity, TransactionClaimEntity claim) {
        return "%s dispute %s %s card %s merchant".formatted(
                claim.getIssueType().name().replace('_', ' ').toLowerCase(),
                claim.getTransactionChannel().name().replace('_', ' ').toLowerCase(),
                claim.isMerchantRecognised() ? "recognised" : "not recognised",
                claim.isCardPossession() ? "in possession" : "not in possession");
    }

    private void record(Map<String, Object> results, String name, Object result) {
        if (result != null) {
            results.put(name, result);
        }
    }

    private EvidenceItemEntity evidenceFrom(FraudCaseEntity entity, TransactionDetails r) {
        Severity severity = !r.found() ? Severity.HIGH
                : r.duplicateFingerprintMatch() ? Severity.HIGH
                : "FAILED".equals(r.authOutcome()) ? Severity.HIGH
                : "UNAVAILABLE".equals(r.authOutcome()) ? Severity.MEDIUM
                : Severity.INFO;
        return caseService.addEvidence(entity, EvidenceType.TRANSACTION, severity,
                r.found()
                        ? "Transaction %s %s %s auth=%s".formatted(r.status(), r.amount(), r.channel(), r.authOutcome())
                        : "Transaction record not found",
                Provenance.TOOL_RESULT, null);
    }

    private EvidenceItemEntity evidenceFrom(FraudCaseEntity entity, DeviceSignals r) {
        Severity severity = !r.deviceKnown() ? Severity.HIGH : Severity.INFO;
        return caseService.addEvidence(entity, EvidenceType.DEVICE, severity,
                r.deviceKnown()
                        ? "Device known, trust %s".formatted(r.trustLevel())
                        : "Newly observed device, trust %s".formatted(r.trustLevel()),
                Provenance.TOOL_RESULT, null);
    }

    private EvidenceItemEntity evidenceFrom(FraudCaseEntity entity, LocationComparison r) {
        Severity severity = switch (r.anomalyLevel() == null ? "NONE" : r.anomalyLevel()) {
            case "HIGH" -> Severity.HIGH;
            case "MEDIUM" -> Severity.MEDIUM;
            default -> Severity.INFO;
        };
        return caseService.addEvidence(entity, EvidenceType.LOCATION, severity,
                "Location anomaly %s (%.1f km)".formatted(r.anomalyLevel(), r.distanceKm()),
                Provenance.TOOL_RESULT, null);
    }

    private EvidenceItemEntity evidenceFrom(FraudCaseEntity entity, MerchantRiskProfile r) {
        Severity severity = r.blocklistMatch() || "HIGH".equals(r.riskBand()) ? Severity.HIGH
                : "MEDIUM".equals(r.riskBand()) ? Severity.MEDIUM : Severity.INFO;
        return caseService.addEvidence(entity, EvidenceType.MERCHANT, severity,
                "Merchant risk %s%s category %s".formatted(
                        r.riskBand(), r.blocklistMatch() ? " (blocklist)" : "", r.category()),
                Provenance.TOOL_RESULT, null);
    }

    private EvidenceItemEntity evidenceFrom(FraudCaseEntity entity, PriorDisputes r) {
        Severity severity = r.similarCount() > 0 ? Severity.MEDIUM : Severity.INFO;
        return caseService.addEvidence(entity, EvidenceType.PRIOR_DISPUTE, severity,
                "%d prior disputes (%d similar) in %d days".formatted(r.count(), r.similarCount(), r.lookbackDays()),
                Provenance.TOOL_RESULT, null);
    }

    private EvidenceItemEntity evidenceFrom(FraudCaseEntity entity, CaseSla r) {
        return caseService.addEvidence(entity, EvidenceType.SLA, Severity.INFO,
                "SLA %s %s target %s".formatted(r.queue(), r.priority(), r.targetAt()),
                Provenance.TOOL_RESULT, null);
    }
}
