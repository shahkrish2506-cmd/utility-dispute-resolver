package com.utilityresolver.exception;

/** Thrown when user input breaks a validation rule (empty name, bad date, ...). */
public class ValidationException extends AppException {
    public ValidationException(String message) {
        super(message);
    }
}
