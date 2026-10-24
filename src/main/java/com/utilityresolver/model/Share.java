package com.utilityresolver.model;

/**
 * Result of splitting a bill: how much one roommate owes and a human-readable
 * explanation of the rule used (this is what makes the split "auditable").
 */
public class Share {
    private final String roommateId;
    private double amount;
    private String explanation;

    public Share(String roommateId, double amount, String explanation) {
        this.roommateId = roommateId;
        this.amount = amount;
        this.explanation = explanation;
    }

    public String getRoommateId() { return roommateId; }

    public double getAmount() { return amount; }

    public void setAmount(double amount) { this.amount = amount; }

    public String getExplanation() { return explanation; }

    /** Combine another share for the same roommate into this one. */
    public void merge(Share other) {
        if (!other.roommateId.equals(roommateId)) {
            throw new IllegalArgumentException("Cannot merge shares of different roommates");
        }
        this.amount += other.amount;
        this.explanation = this.explanation + " + " + other.explanation;
    }

    public void appendExplanation(String text) {
        this.explanation = this.explanation + text;
    }
}
