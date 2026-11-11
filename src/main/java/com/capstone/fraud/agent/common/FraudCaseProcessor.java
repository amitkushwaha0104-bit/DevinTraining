package com.capstone.fraud.agent.common;

/**
 * Framework-neutral processor contract. Implementations: rules mode
 * (deterministic fallback) and Spring AI adapter. The processor only
 * recommends; it never executes financial, customer-facing or
 * account-control actions.
 */
public interface FraudCaseProcessor {
    FraudCaseRecommendation analyze(FraudCaseContext context);
}
