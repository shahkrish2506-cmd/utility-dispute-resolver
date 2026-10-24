package com.utilityresolver.web;

/** Unchecked exception carrying an HTTP status code (used only inside the web layer). */
public class HttpError extends RuntimeException {
    private final int status;

    public HttpError(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
