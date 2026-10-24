package com.utilityresolver.exception;

/** Thrown when a bill cannot be split (no participants, zero total usage, wrong strategy...). */
public class InvalidBillException extends ValidationException {
    public InvalidBillException(String message) {
        super(message);
    }
}
