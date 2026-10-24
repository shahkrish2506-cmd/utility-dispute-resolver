package com.utilityresolver.model;

import com.utilityresolver.exception.ValidationException;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Money one roommate pays another to settle utility shares.
 * ASSOCIATION: refers to two Roommates by id; neither owns the other.
 */
public class Payment implements Identifiable {
    private final String id;
    private final String fromId;
    private final String toId;
    private final double amount;
    private final LocalDate date;
    private final YearMonth month;
    private final String note;

    public Payment(String id, String fromId, String toId, double amount,
                   LocalDate date, YearMonth month, String note) throws ValidationException {
        if (fromId == null || toId == null || fromId.isBlank() || toId.isBlank()) {
            throw new ValidationException("Payer and receiver are required");
        }
        if (fromId.equals(toId)) {
            throw new ValidationException("A roommate cannot pay themselves");
        }
        if (amount <= 0) {
            throw new ValidationException("Payment amount must be greater than zero");
        }
        if (date == null || month == null) {
            throw new ValidationException("Payment date and month are required");
        }
        this.id = id;
        this.fromId = fromId;
        this.toId = toId;
        this.amount = amount;
        this.date = date;
        this.month = month;
        this.note = note == null ? "" : note.trim();
    }

    @Override
    public String getId() { return id; }

    public String getFromId() { return fromId; }

    public String getToId() { return toId; }

    public double getAmount() { return amount; }

    public LocalDate getDate() { return date; }

    public YearMonth getMonth() { return month; }

    public String getNote() { return note; }
}
