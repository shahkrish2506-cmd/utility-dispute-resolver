package com.utilityresolver.strategy;

import com.utilityresolver.exception.InvalidBillException;
import com.utilityresolver.model.Share;
import com.utilityresolver.model.UsageRecord;

import java.util.ArrayList;
import java.util.List;

/** Each roommate pays in proportion to the units they consumed. */
public class UsageBasedSplit extends AbstractSplitStrategy {

    public UsageBasedSplit() {
        super("USAGE", "Usage-based split", "Each roommate pays in proportion to the units they used (meter readings).");
    }

    @Override
    public boolean requiresUsage() {
        return true;
    }

    @Override
    protected List<Share> computeShares(double amount, List<UsageRecord> participants, String unitLabel)
            throws InvalidBillException {
        double totalUnits = 0;
        for (UsageRecord r : participants) {
            totalUnits += r.getUnits();
        }
        if (totalUnits <= 0) {
            throw new InvalidBillException("Usage-based split needs at least one usage reading greater than zero");
        }
        List<Share> shares = new ArrayList<>();
        for (UsageRecord r : participants) {
            double fraction = r.getUnits() / totalUnits;
            String why = trim(r.getUnits()) + " of " + trim(totalUnits) + " " + unitLabel
                    + " (" + pct(fraction) + ") x " + money(amount);
            shares.add(new Share(r.getRoommateId(), amount * fraction, why));
        }
        return shares;
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.2f", v);
    }
}
