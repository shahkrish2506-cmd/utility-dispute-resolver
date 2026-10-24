package com.utilityresolver.service;

import com.utilityresolver.exception.AppException;
import com.utilityresolver.exception.ValidationException;
import com.utilityresolver.model.Bill;
import com.utilityresolver.model.Dispute;
import com.utilityresolver.model.DisputeStatus;
import com.utilityresolver.storage.DataStore;

import java.io.IOException;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Raise, review, resolve and reject disputes about bills. */
public class DisputeService {
    private final DataStore store;
    private final RoommateService roommates;
    private final PeriodService periods;

    public DisputeService(DataStore store, RoommateService roommates, PeriodService periods) {
        this.store = store;
        this.roommates = roommates;
        this.periods = periods;
    }

    public List<Dispute> all() {
        return store.disputes().findAll().stream()
                .sorted(Comparator.comparing((Dispute d) -> !d.getStatus().isUnresolved())
                        .thenComparing(Dispute::getRaisedOn, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    public List<Dispute> forMonth(YearMonth month) {
        return all().stream().filter(d -> monthOf(d) != null && monthOf(d).equals(month)).collect(Collectors.toList());
    }

    public List<Dispute> unresolvedFor(YearMonth month) {
        return forMonth(month).stream().filter(d -> d.getStatus().isUnresolved()).collect(Collectors.toList());
    }

    public long unresolvedCount() {
        return store.disputes().findWhere(d -> d.getStatus().isUnresolved()).size();
    }

    public YearMonth monthOf(Dispute d) {
        return store.bills().findById(d.getBillId()).map(Bill::getMonth).orElse(null);
    }

    public Dispute raise(String billId, String raisedById, String reason) throws AppException, IOException {
        Bill bill = store.bills().getOrThrow(billId);
        roommates.get(raisedById);
        periods.ensureEditable(bill.getMonth());
        Dispute d = new Dispute(store.disputeIds().next(), billId, raisedById, reason);
        store.disputes().save(d);
        store.audit().log("DISPUTE_RAISED", d.getId() + " by " + roommates.nameOf(raisedById) + " on " + billId
                + ": " + d.getReason());
        return d;
    }

    public Dispute changeStatus(String id, String status, String note) throws AppException, IOException {
        Dispute d = store.disputes().getOrThrow(id);
        DisputeStatus target;
        try {
            target = DisputeStatus.valueOf(status.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new ValidationException("Unknown dispute status: " + status);
        }
        YearMonth m = monthOf(d);
        if (m != null) {
            periods.ensureEditable(m);
        }
        switch (target) {
            case UNDER_REVIEW: d.startReview(note); break;
            case RESOLVED: d.resolve(note); break;
            case REJECTED: d.reject(note); break;
            case OPEN: d.reopen(); break;
            default: throw new ValidationException("Unsupported status " + target);
        }
        store.disputes().flush();
        store.audit().log("DISPUTE_" + target.name(), id + (note == null || note.isBlank() ? "" : ": " + note));
        return d;
    }
}
