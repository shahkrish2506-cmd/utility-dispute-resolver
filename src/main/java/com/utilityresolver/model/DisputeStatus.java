package com.utilityresolver.model;

/** Lifecycle of a dispute. OPEN and UNDER_REVIEW count as "unresolved". */
public enum DisputeStatus {
    OPEN("Open"),
    UNDER_REVIEW("Under review"),
    RESOLVED("Resolved"),
    REJECTED("Rejected");

    private final String label;

    DisputeStatus(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }

    public boolean isUnresolved() {
        return this == OPEN || this == UNDER_REVIEW;
    }
}
