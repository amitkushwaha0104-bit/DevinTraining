package com.capstone.fraud.domain;

import java.util.EnumSet;
import java.util.Set;

public enum CaseStatus {
    NEW,
    ENRICHING,
    INCOMPLETE,
    READY_FOR_REVIEW,
    UNDER_REVIEW,
    ROUTED,
    CLOSED;

    private static final Set<CaseStatus> NEW_NEXT = EnumSet.of(ENRICHING);
    private static final Set<CaseStatus> ENRICHING_NEXT = EnumSet.of(READY_FOR_REVIEW, INCOMPLETE);
    private static final Set<CaseStatus> INCOMPLETE_NEXT = EnumSet.of(ENRICHING, CLOSED);
    private static final Set<CaseStatus> READY_NEXT = EnumSet.of(UNDER_REVIEW);
    private static final Set<CaseStatus> UNDER_REVIEW_NEXT = EnumSet.of(ROUTED, CLOSED, INCOMPLETE);

    public boolean canTransitionTo(CaseStatus target) {
        return switch (this) {
            case NEW -> NEW_NEXT.contains(target);
            case ENRICHING -> ENRICHING_NEXT.contains(target);
            case INCOMPLETE -> INCOMPLETE_NEXT.contains(target);
            case READY_FOR_REVIEW -> READY_NEXT.contains(target);
            case UNDER_REVIEW -> UNDER_REVIEW_NEXT.contains(target);
            case ROUTED, CLOSED -> false;
        };
    }
}
