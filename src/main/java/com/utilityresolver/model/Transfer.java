package com.utilityresolver.model;

/** A suggested "X pays Y amount" step produced by the settle-up algorithm. */
public class Transfer {
    private final String fromId;
    private final String toId;
    private final double amount;

    public Transfer(String fromId, String toId, double amount) {
        this.fromId = fromId;
        this.toId = toId;
        this.amount = amount;
    }

    public String getFromId() { return fromId; }

    public String getToId() { return toId; }

    public double getAmount() { return amount; }
}
