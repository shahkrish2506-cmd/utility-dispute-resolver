package com.utilityresolver.service;

import com.utilityresolver.exception.MonthLockedException;
import com.utilityresolver.exception.UnresolvedDisputeException;
import com.utilityresolver.model.Dispute;
import com.utilityresolver.storage.DataStore;

import java.io.IOException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Handles finalising ("locking") a month once everything is settled. */
public class PeriodService {
    private final DataStore store;
    private DisputeService disputeService;

    public PeriodService(DataStore store) {
        this.store = store;
    }

    void setDisputeService(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    public boolean isFinalized(YearMonth month) {
        return store.getFinalizedMonths().contains(month);
    }

    public void ensureEditable(YearMonth month) throws MonthLockedException {
        if (isFinalized(month)) {
            throw new MonthLockedException(month.toString());
        }
    }

    /** Locks the month. Fails with UnresolvedDisputeException if any dispute is still open. */
    public void finalizeMonth(YearMonth month) throws UnresolvedDisputeException, IOException {
        List<String> open = new ArrayList<>();
        for (Dispute d : disputeService.unresolvedFor(month)) {
            open.add(d.getId());
        }
        if (!open.isEmpty()) {
            throw new UnresolvedDisputeException(month.toString(), open);
        }
        store.finalizedMonthsMutable().add(month);
        store.saveFinalizedMonths();
        store.audit().log("MONTH_FINALIZED", month.toString());
    }

    public void reopenMonth(YearMonth month) throws IOException {
        if (store.finalizedMonthsMutable().remove(month)) {
            store.saveFinalizedMonths();
            store.audit().log("MONTH_REOPENED", month.toString());
        }
    }
}
