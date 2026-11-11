package com.capstone.fraud.agent.common;

import java.util.List;

public class InvalidRecommendationException extends RuntimeException {

    private final List<String> violations;

    public InvalidRecommendationException(List<String> violations) {
        super("Invalid recommendation: " + String.join("; ", violations));
        this.violations = violations;
    }

    public List<String> violations() {
        return violations;
    }
}
