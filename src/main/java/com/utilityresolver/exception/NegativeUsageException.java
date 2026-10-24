package com.utilityresolver.exception;

/** Thrown when a meter reading / usage value or occupancy days is negative. */
public class NegativeUsageException extends ValidationException {
    private final double value;

    public NegativeUsageException(String field, double value) {
        super(field + " cannot be negative (got " + value + ")");
        this.value = value;
    }

    public double getValue() {
        return value;
    }
}
