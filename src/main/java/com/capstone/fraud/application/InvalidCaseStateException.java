package com.capstone.fraud.application;

import com.capstone.fraud.domain.CaseStatus;

public class InvalidCaseStateException extends RuntimeException {
    public InvalidCaseStateException(String caseId, CaseStatus from, CaseStatus to) {
        super("Case %s cannot transition from %s to %s".formatted(caseId, from, to));
    }
}
