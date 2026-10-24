package com.utilityresolver.exception;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when someone tries to finalise (lock) a month while disputes
 * on that month's bills are still open.
 */
public class UnresolvedDisputeException extends AppException {
    private final List<String> disputeIds;

    public UnresolvedDisputeException(String month, List<String> disputeIds) {
        super("Cannot finalise " + month + ": " + disputeIds.size()
                + " dispute(s) still unresolved " + disputeIds);
        this.disputeIds = Collections.unmodifiableList(disputeIds);
    }

    public List<String> getDisputeIds() {
        return disputeIds;
    }
}
