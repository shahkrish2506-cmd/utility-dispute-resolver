package com.utilityresolver.model;

import com.utilityresolver.exception.ValidationException;

import java.time.LocalDate;

/**
 * A complaint raised by a roommate about a specific bill
 * (e.g. "my meter reading is wrong", "I was away for 10 days").
 */
public class Dispute implements Identifiable {
    private final String id;
    private final String billId;
    private final String raisedById;
    private final String reason;
    private final LocalDate raisedOn;
    private DisputeStatus status;
    private String resolutionNote;
    private LocalDate closedOn;

    /** Constructor for a brand-new dispute. */
    public Dispute(String id, String billId, String raisedById, String reason) throws ValidationException {
        this(id, billId, raisedById, reason, LocalDate.now(), DisputeStatus.OPEN, "", null);
    }

    /** Full constructor (used when loading from file). */
    public Dispute(String id, String billId, String raisedById, String reason, LocalDate raisedOn,
                   DisputeStatus status, String resolutionNote, LocalDate closedOn) throws ValidationException {
        if (billId == null || billId.isBlank()) {
            throw new ValidationException("Dispute must refer to a bill");
        }
        if (raisedById == null || raisedById.isBlank()) {
            throw new ValidationException("Dispute must be raised by a roommate");
        }
        if (reason == null || reason.trim().length() < 5) {
            throw new ValidationException("Please describe the dispute (at least 5 characters)");
        }
        this.id = id;
        this.billId = billId;
        this.raisedById = raisedById;
        this.reason = reason.trim();
        this.raisedOn = raisedOn;
        this.status = status;
        this.resolutionNote = resolutionNote == null ? "" : resolutionNote;
        this.closedOn = closedOn;
    }

    public void startReview(String note) throws ValidationException {
        if (!status.isUnresolved()) {
            throw new ValidationException("Dispute " + id + " is already closed");
        }
        status = DisputeStatus.UNDER_REVIEW;
        if (note != null && !note.isBlank()) {
            resolutionNote = note.trim();
        }
    }

    public void resolve(String note) throws ValidationException {
        close(DisputeStatus.RESOLVED, note);
    }

    public void reject(String note) throws ValidationException {
        close(DisputeStatus.REJECTED, note);
    }

    public void reopen() {
        status = DisputeStatus.OPEN;
        closedOn = null;
    }

    private void close(DisputeStatus newStatus, String note) throws ValidationException {
        if (!status.isUnresolved()) {
            throw new ValidationException("Dispute " + id + " is already closed");
        }
        if (note == null || note.isBlank()) {
            throw new ValidationException("A resolution note is required to close a dispute");
        }
        status = newStatus;
        resolutionNote = note.trim();
        closedOn = LocalDate.now();
    }

    @Override
    public String getId() { return id; }

    public String getBillId() { return billId; }

    public String getRaisedById() { return raisedById; }

    public String getReason() { return reason; }

    public LocalDate getRaisedOn() { return raisedOn; }

    public DisputeStatus getStatus() { return status; }

    public String getResolutionNote() { return resolutionNote; }

    public LocalDate getClosedOn() { return closedOn; }
}
