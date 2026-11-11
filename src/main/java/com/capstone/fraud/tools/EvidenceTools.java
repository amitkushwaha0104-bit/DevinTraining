package com.capstone.fraud.tools;

import com.capstone.fraud.knowledge.PolicyPassage;
import com.capstone.fraud.knowledge.PolicyRetriever;
import com.capstone.fraud.persistence.AuditWriter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static com.capstone.fraud.tools.ToolResults.AuditEventResult;
import static com.capstone.fraud.tools.ToolResults.CaseSla;
import static com.capstone.fraud.tools.ToolResults.DeviceSignals;
import static com.capstone.fraud.tools.ToolResults.LocationComparison;
import static com.capstone.fraud.tools.ToolResults.MerchantRiskProfile;
import static com.capstone.fraud.tools.ToolResults.PolicyEvidence;
import static com.capstone.fraud.tools.ToolResults.PriorDisputes;
import static com.capstone.fraud.tools.ToolResults.TransactionDetails;

/**
 * Allow-listed, read-only evidence tools. Results are deterministic
 * synthetic fixtures: identical input produces identical output. No
 * tool can block a card, issue a refund, move funds, file a report or
 * contact a customer.
 */
@Component
public class EvidenceTools {

    private final DeterministicFixtures fixtures;
    private final PolicyRetriever policyRetriever;
    private final AuditWriter auditWriter;
    private final AtomicLong resultSeq = new AtomicLong(100);

    public EvidenceTools(DeterministicFixtures fixtures, PolicyRetriever policyRetriever, AuditWriter auditWriter) {
        this.fixtures = fixtures;
        this.policyRetriever = policyRetriever;
        this.auditWriter = auditWriter;
    }

    @Tool(description = "Retrieve transaction state, channel and authentication fields for a masked transaction reference.")
    public TransactionDetails getTransactionDetails(
            @ToolParam(description = "Masked transaction reference, e.g. TXN-2026-784102") String transactionRef) {
        var scenario = fixtures.scenarioFor(transactionRef);
        Random r = fixtures.randomFor("txn", transactionRef);
        Instant observed = Instant.now();

        if (scenario == DeterministicFixtures.Scenario.MISSING) {
            return new TransactionDetails(resultId(), observed, false, "UNKNOWN", null, null, null,
                    "UNAVAILABLE", null, false,
                    List.of("Transaction reference not found in synthetic ledger."));
        }

        boolean found = scenario != DeterministicFixtures.Scenario.HASHED || r.nextInt(100) >= 8;
        if (!found) {
            return new TransactionDetails(resultId(), observed, false, "UNKNOWN", null, null, null,
                    "UNAVAILABLE", null, false,
                    List.of("Transaction reference not found in synthetic ledger."));
        }

        return switch (scenario) {
            case UNAUTHORISED -> new TransactionDetails(resultId(), observed, true, "POSTED",
                    Instant.now().minus(2, ChronoUnit.DAYS), new BigDecimal("48750.00"), "CARD_NOT_PRESENT",
                    "UNAVAILABLE", "NORTHSTAR DIGITAL ••••73", false,
                    List.of("Posted card-not-present transaction.",
                            "Authentication outcome unavailable for this transaction."));
            case DUPLICATE -> new TransactionDetails(resultId(), observed, true, "POSTED",
                    Instant.now().minus(1, ChronoUnit.DAYS), new BigDecimal("1299.00"), "CARD_PRESENT",
                    "AUTHENTICATED", "CITY MART ••••11", true,
                    List.of("Matching payment fingerprint found for a second posting.",
                            "Duplicate posting candidate confirmed."));
            case MERCHANT_RECOGNISED -> new TransactionDetails(resultId(), observed, true, "SETTLED",
                    Instant.now().minus(5, ChronoUnit.DAYS), new BigDecimal("2499.00"), "CARD_NOT_PRESENT",
                    "AUTHENTICATED", "STREAMFLIX ••••88", false,
                    List.of("Settled transaction at a recurring-billing merchant."));
            case HASHED -> new TransactionDetails(resultId(), observed, true,
                    pick(r, "POSTED", "SETTLED", "PENDING"),
                    Instant.now().minus(r.nextInt(720), ChronoUnit.HOURS),
                    BigDecimal.valueOf(50 + r.nextInt(2000)),
                    pick(r, "CARD_NOT_PRESENT", "CARD_PRESENT", "BANK_TRANSFER"),
                    pick(r, "AUTHENTICATED", "AUTHENTICATED", "UNAVAILABLE", "FAILED"),
                    "MERCHANT ••••" + (10 + r.nextInt(89)), false,
                    List.of("Synthetic transaction record."));
            default -> throw new IllegalStateException("unreachable");
        };
    }

    @Tool(description = "Check device novelty and trust indicators for the masked customer.")
    public DeviceSignals getDeviceSignals(
            @ToolParam(description = "Masked customer reference, e.g. CUST-••••-4821") String maskedCustomerRef,
            @ToolParam(description = "Masked transaction reference for scenario context") String transactionRef) {
        var scenario = fixtures.scenarioFor(transactionRef);
        Random r = fixtures.randomFor("device", maskedCustomerRef);
        Instant observed = Instant.now();

        return switch (scenario) {
            case UNAUTHORISED -> new DeviceSignals(resultId(), observed, false,
                    Instant.now().minus(6, ChronoUnit.HOURS), "LOW",
                    List.of("Newly observed device fingerprint.",
                            "Device first seen within hours of the disputed transaction."));
            case DUPLICATE, MERCHANT_RECOGNISED -> new DeviceSignals(resultId(), observed, true,
                    Instant.now().minus(300, ChronoUnit.DAYS), "HIGH",
                    List.of("Device previously used for trusted activity."));
            case HASHED -> {
                boolean known = r.nextBoolean();
                yield new DeviceSignals(resultId(), observed, known,
                        Instant.now().minus(known ? 30 + r.nextInt(400) : r.nextInt(48), ChronoUnit.HOURS),
                        known ? pick(r, "MEDIUM", "HIGH") : pick(r, "LOW", "MEDIUM"),
                        known ? List.of("Device previously observed.") : List.of("Newly observed device fingerprint."));
            }
            default -> new DeviceSignals(resultId(), observed, false, null, "UNKNOWN",
                    List.of("Device signals unavailable."));
        };
    }

    @Tool(description = "Compare the transaction location with recent trusted locations for the masked customer.")
    public LocationComparison compareRecentLocations(
            @ToolParam(description = "Masked customer reference") String maskedCustomerRef,
            @ToolParam(description = "Masked transaction reference for scenario context") String transactionRef) {
        var scenario = fixtures.scenarioFor(transactionRef);
        Random r = fixtures.randomFor("location", maskedCustomerRef);
        Instant observed = Instant.now();

        return switch (scenario) {
            case UNAUTHORISED -> new LocationComparison(resultId(), observed, 1840.5, 47, "HIGH",
                    List.of("Transaction location is inconsistent with recent trusted activity.",
                            "Impossible-travel style pattern relative to last trusted session."));
            case DUPLICATE, MERCHANT_RECOGNISED -> new LocationComparison(resultId(), observed, 4.2, 2880, "NONE",
                    List.of("Location consistent with recent trusted activity."));
            case HASHED -> {
                double km = Math.round(r.nextDouble() * 2000 * 10.0) / 10.0;
                String level = km > 800 ? "HIGH" : km > 150 ? "MEDIUM" : "NONE";
                yield new LocationComparison(resultId(), observed, km, r.nextInt(4320), level,
                        List.of(level.equals("NONE")
                                ? "Location consistent with recent trusted activity."
                                : "Location deviates from recent trusted activity."));
            }
            default -> new LocationComparison(resultId(), observed, 0, 0, "UNKNOWN",
                    List.of("Location comparison unavailable."));
        };
    }

    @Tool(description = "Return synthetic merchant characteristics for a masked merchant descriptor.")
    public MerchantRiskProfile getMerchantRiskProfile(
            @ToolParam(description = "Masked merchant descriptor, e.g. NORTHSTAR DIGITAL ••••73") String maskedMerchant,
            @ToolParam(description = "Masked transaction reference for scenario context") String transactionRef) {
        var scenario = fixtures.scenarioFor(transactionRef);
        Random r = fixtures.randomFor("merchant", maskedMerchant);
        Instant observed = Instant.now();

        return switch (scenario) {
            case UNAUTHORISED -> new MerchantRiskProfile(resultId(), observed, "HIGH", false,
                    "ONLINE_DIGITAL_SERVICES",
                    List.of("Merchant sits in a high-dispute synthetic category.",
                            "No blocklist match on file."));
            case DUPLICATE -> new MerchantRiskProfile(resultId(), observed, "LOW", false, "GROCERY_RETAIL",
                    List.of("Low-risk merchant category."));
            case MERCHANT_RECOGNISED -> new MerchantRiskProfile(resultId(), observed, "LOW", false, "SUBSCRIPTION_MEDIA",
                    List.of("Recognised subscription merchant.",
                            "Disputes here are usually fulfilment or cancellation related."));
            case HASHED -> new MerchantRiskProfile(resultId(), observed,
                    pick(r, "LOW", "MEDIUM", "MEDIUM", "HIGH"), r.nextInt(100) < 4,
                    pick(r, "ONLINE_DIGITAL_SERVICES", "TRAVEL", "GROCERY_RETAIL", "SUBSCRIPTION_MEDIA"),
                    List.of("Synthetic merchant profile."));
            default -> new MerchantRiskProfile(resultId(), observed, "UNKNOWN", false, "UNKNOWN",
                    List.of("Merchant profile unavailable."));
        };
    }

    @Tool(description = "Summarise earlier disputes for the masked customer over a lookback window.")
    public PriorDisputes getPriorDisputes(
            @ToolParam(description = "Masked customer reference") String maskedCustomerRef,
            @ToolParam(description = "Masked transaction reference for scenario context") String transactionRef) {
        var scenario = fixtures.scenarioFor(transactionRef);
        Random r = fixtures.randomFor("disputes", maskedCustomerRef);
        Instant observed = Instant.now();

        return switch (scenario) {
            case UNAUTHORISED -> new PriorDisputes(resultId(), observed, 1, 0, 365,
                    List.of("One earlier dispute in the lookback window; none similar to this claim."));
            case DUPLICATE, MERCHANT_RECOGNISED -> new PriorDisputes(resultId(), observed, 0, 0, 365,
                    List.of("No earlier disputes in the lookback window."));
            case HASHED -> {
                int count = r.nextInt(4);
                yield new PriorDisputes(resultId(), observed, count, r.nextInt(count + 1), 365,
                        List.of(count == 0
                                ? "No earlier disputes in the lookback window."
                                : count + " earlier dispute(s) in the lookback window."));
            }
            default -> new PriorDisputes(resultId(), observed, 0, 0, 365,
                    List.of("Prior-dispute history unavailable."));
        };
    }

    @Tool(description = "Calculate a training SLA target for a proposed route and priority.")
    public CaseSla calculateCaseSla(
            @ToolParam(description = "Proposed route, e.g. FRAUD_INVESTIGATION") String route,
            @ToolParam(description = "Risk level, e.g. HIGH") String riskLevel) {
        Instant observed = Instant.now();
        long hours = switch (safe(route)) {
            case "FRAUD_INVESTIGATION" -> "HIGH".equals(safe(riskLevel)) ? 4 : 24;
            case "DISPUTE_OPERATIONS" -> 72;
            case "PAYMENT_OPERATIONS" -> 48;
            default -> 120;
        };
        String priority = switch (safe(riskLevel)) {
            case "HIGH" -> "P1";
            case "MEDIUM" -> "P2";
            default -> "P3";
        };
        Instant target = observed.plus(Duration.ofHours(hours));
        return new CaseSla(resultId(), observed, safe(route), priority, target,
                "Training SLA: %s route at %s priority targets resolution within %d hours."
                        .formatted(safe(route), priority, hours));
    }

    @Tool(description = "Search approved policy passages for text relevant to the case query.")
    public PolicyEvidence retrievePolicyEvidence(
            @ToolParam(description = "Natural-language query describing the dispute and evidence") String query) {
        List<PolicyPassage> passages = policyRetriever.retrieve(query);
        return new PolicyEvidence(resultId(), Instant.now(), passages.isEmpty(),
                passages.stream()
                        .map(p -> new PolicyEvidence.Passage(
                                p.documentId(), p.version(), p.section(), p.chunkId(), p.score(), p.passage()))
                        .toList());
    }

    @Tool(description = "Append an important audit event for a case. Read/write to the audit trail only - no business action.")
    public AuditEventResult saveAuditEvent(
            @ToolParam(description = "Case identifier, e.g. CASE-2026-0042") String caseId,
            @ToolParam(description = "Event type, e.g. FOLLOW_UP_QUESTION") String eventType,
            @ToolParam(description = "Short non-sensitive summary") String summary) {
        var event = auditWriter.record(caseId, eventType, "TOOL", "model", summary);
        return new AuditEventResult(event.getEventId(), event.getEventType(), event.getRecordedAt());
    }

    private String resultId() {
        return "TRES-" + resultSeq.incrementAndGet();
    }

    private static String safe(String v) {
        return v == null || v.isBlank() ? "MANUAL_REVIEW" : v;
    }

    private static String pick(Random r, String... options) {
        return options[r.nextInt(options.length)];
    }
}
