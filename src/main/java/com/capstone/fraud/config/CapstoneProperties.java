package com.capstone.fraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "capstone")
public record CapstoneProperties(
        String processorMode,
        boolean humanReviewRequired,
        boolean allowFinancialActions,
        boolean allowCustomerContact,
        Retrieval retrieval,
        Memory memory,
        Tools tools) {

    public record Retrieval(int topK, double similarityThreshold) {
    }

    public record Memory(int maxMessages) {
    }

    public record Tools(long timeoutMs, String deterministicSeed) {
    }
}
