package com.capstone.fraud.application;

public class CaseNotFoundException extends RuntimeException {
    public CaseNotFoundException(String caseId) {
        super("Case not found: " + caseId);
    }
}
