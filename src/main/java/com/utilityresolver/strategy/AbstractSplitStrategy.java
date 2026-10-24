package com.utilityresolver.strategy;

import com.utilityresolver.exception.InvalidBillException;
import com.utilityresolver.model.Share;
import com.utilityresolver.model.UsageRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * ABSTRACT helper implementing the parts every strategy shares
 * (template method: split() validates, then calls computeShares()).
 */
public abstract class AbstractSplitStrategy implements SplitStrategy {
    private final String key;
    private final String displayName;
    private final String description;

    protected AbstractSplitStrategy(String key, String displayName, String description) {
        this.key = key;
        this.displayName = displayName;
        this.description = description;
    }

    @Override
    public String getKey() { return key; }

    @Override
    public String getDisplayName() { return displayName; }

    @Override
    public String getDescription() { return description; }

    @Override
    public boolean requiresUsage() { return false; }

    @Override
    public final List<Share> split(double amount, List<UsageRecord> records, String unitLabel)
            throws InvalidBillException {
        if (amount < 0) {
            throw new InvalidBillException("Amount to split cannot be negative");
        }
        List<UsageRecord> participants = new ArrayList<>();
        for (UsageRecord r : records) {
            if (r.isParticipant()) {
                participants.add(r);
            }
        }
        if (participants.isEmpty()) {
            throw new InvalidBillException("No roommates to split between");
        }
        return computeShares(amount, participants, unitLabel);
    }

    /** Each concrete strategy supplies only its own maths. */
    protected abstract List<Share> computeShares(double amount, List<UsageRecord> participants, String unitLabel)
            throws InvalidBillException;

    protected static String money(double v) {
        return com.utilityresolver.util.Money.format(v);
    }

    protected static String pct(double fraction) {
        return String.format("%.1f%%", fraction * 100);
    }
}
