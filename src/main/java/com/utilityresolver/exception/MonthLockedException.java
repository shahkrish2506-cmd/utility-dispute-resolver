package com.utilityresolver.exception;

/** Thrown when someone edits bills or payments of a month that has already been finalised. */
public class MonthLockedException extends AppException {
    public MonthLockedException(String month) {
        super("Month " + month + " is finalised and cannot be changed. Re-open it first.");
    }
}
