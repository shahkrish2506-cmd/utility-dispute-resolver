package com.utilityresolver.exception;

/**
 * Base checked exception for every business-rule failure in the application.
 * All custom exceptions extend this class so callers can catch them together.
 */
public class AppException extends Exception {
    public AppException(String message) {
        super(message);
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
    }
}
