package com.capstone.fraud.application;

import com.capstone.fraud.agent.common.FraudCaseContext;
import com.capstone.fraud.agent.common.IntakeSnapshot;
import com.capstone.fraud.agent.common.EvidenceSnapshot;
import com.capstone.fraud.agent.common.ToolTraceSnapshot;
import com.capstone.fraud.api.dto.CaseMessageResponse;
import com.capstone.fraud.api.dto.RecommendationResponse;
import com.capstone.fraud.config.CapstoneProperties;
import com.capstone.fraud.knowledge.PolicyPassage;
import com.capstone.fraud.knowledge.PolicyRetriever;
import com.capstone.fraud.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Case-scoped follow-up questions. Memory is keyed by caseId in both
 * modes, so context can never bleed between cases. In rules mode the
 * answer is a deterministic grounded summary; in spring-ai mode the
 * model answers with the same evidence and citation model.
 */
@Service
public class CaseQaService {

    private static final Logger log = LoggerFactory.getLogger(CaseQaService.class);

    private final FraudCaseService caseService;
    private final PolicyRetriever policyRetriever;
    private final AuditWriter auditWriter;
    private final CapstoneProperties properties;
    private final ObjectProvider<ChatClient> chatClient;
    private final ObjectProvider<ChatMemory> chatMemory;

    public CaseQaService(FraudCaseService caseService,
                         PolicyRetriever policyRetriever,
                         AuditWriter auditWriter,
                         CapstoneProperties properties,
                         ObjectProvider<ChatClient> chatClient,
                         ObjectProvider<ChatMemory> chatMemory) {
        this.caseService = caseService;
        this.policyRetriever = policyRetriever;
        this.auditWriter = auditWriter;
        this.properties = properties;
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
    }

    public CaseMessageResponse ask(String caseId, String question, String actorId) {
        FraudCaseEntity entity = caseService.requireCase(caseId);
        TransactionClaimEntity claim = caseService.claimFor(entity);
        auditWriter.record(entity.getId(), "FOLLOW_UP_QUESTION", "USER", actorId,
                "question received (%d chars)".formatted(question.length()));

        List<PolicyPassage> passages = policyRetriever.retrieve(question);
        List<RecommendationResponse.Citation> citations = passages.stream()
                .map(p -> new RecommendationResponse.Citation(
                        p.documentId(), p.version(), p.section(), p.chunkId(), p.score(), p.passage()))
                .toList();

        boolean aiMode = "spring-ai".equalsIgnoreCase(properties.processorMode())
                && chatClient.getIfAvailable() != null;
        String answer;
        if (aiMode) {
            answer = askModel(entity, claim, question, passages);
        } else {
            answer = deterministicAnswer(entity, claim, question, passages);
        }
        auditWriter.record(entity.getId(), "FOLLOW_UP_ANSWERED", aiMode ? "MODEL" : "SYSTEM",
                actorId, "answer produced with %d citations".formatted(citations.size()));
        return new CaseMessageResponse(caseId, answer, citations);
    }

    private String askModel(FraudCaseEntity entity, TransactionClaimEntity claim,
                            String question, List<PolicyPassage> passages) {
        try {
            var context = new FraudCaseContext(entity.getCaseId(), snapshot(entity, claim),
                    evidence(entity), passages, traces(entity), "", java.util.Map.of());
            return chatClient.getObject().prompt()
                    .system("You answer follow-up questions about one fraud case using only the provided "
                            + "validated facts, evidence and policy passages. Never invent evidence, never "
                            + "take actions, and remind the user the final decision stays with the human reviewer.")
                    .user(u -> u.text("""
                            {caseSummary}

                            EVIDENCE:
                            {toolEvidence}

                            POLICY:
                            {policyContext}

                            QUESTION: {question}
                            """).params(java.util.Map.of(
                            "caseSummary", context.renderCaseSummary(),
                            "toolEvidence", context.renderToolEvidence(),
                            "policyContext", context.renderPolicyContext(),
                            "question", question)))
                    .advisors(a -> a
                            .advisors(MessageChatMemoryAdvisor.builder(chatMemory.getObject()).build())
                            .param(ChatMemory.CONVERSATION_ID, entity.getCaseId()))
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("follow-up model call failed for {}: {}", entity.getCaseId(), e.toString());
            return deterministicAnswer(entity, claim, question, passages);
        }
    }

    private String deterministicAnswer(FraudCaseEntity entity, TransactionClaimEntity claim,
                                       String question, List<PolicyPassage> passages) {
        RecommendationEntity latest = caseService.latestRecommendation(entity);
        StringBuilder sb = new StringBuilder();
        if (latest != null) {
            sb.append("Latest recommendation: ").append(latest.getClassification())
                    .append(" → ").append(latest.getRecommendedRoute())
                    .append(" (risk ").append(latest.getRiskLevel())
                    .append(", confidence ").append(String.format("%.2f", latest.getConfidence()))
                    .append("). ");
            List<String> missing = caseService.jsonToList(latest.getMissingInformationJson());
            if (!missing.isEmpty()) {
                sb.append("Missing information: ").append(String.join("; ", missing)).append(". ");
            }
        } else {
            sb.append("No recommendation has been produced yet. ");
        }
        if (passages.isEmpty()) {
            sb.append("No policy passage met the retrieval threshold for this question.");
        } else {
            sb.append("Relevant policy: ").append(passages.getFirst().documentId())
                    .append(" v").append(passages.getFirst().version())
                    .append(" section ").append(passages.getFirst().section()).append(".");
        }
        sb.append(" Final disposition remains with a human reviewer.");
        return sb.toString();
    }

    private IntakeSnapshot snapshot(FraudCaseEntity e, TransactionClaimEntity c) {
        return new IntakeSnapshot(e.getMaskedCustomerRef(), e.getHomeRegion(), c.getTransactionRef(),
                c.getAmount(), c.getCurrency(), c.getOccurredAt(), c.getTransactionStatus().name(),
                c.getTransactionChannel().name(), c.getMaskedMerchant(), c.getIssueType().name(),
                c.getCustomerStatement(), c.isCardPossession(), c.isMerchantRecognised());
    }

    private List<EvidenceSnapshot> evidence(FraudCaseEntity e) {
        return caseService.evidenceFor(e).stream()
                .map(x -> new EvidenceSnapshot(x.getEvidenceId(), x.getType().name(), x.getSeverity().name(),
                        x.getValueSummary(), x.getProvenance().name(), x.getObservedAt()))
                .toList();
    }

    private List<ToolTraceSnapshot> traces(FraudCaseEntity e) {
        return caseService.tracesFor(e).stream()
                .map(t -> new ToolTraceSnapshot(t.getTraceId(), t.getToolName(), t.getStatus().name(),
                        t.getDurationMs(), t.getResultSummary()))
                .toList();
    }
}
