package com.utilityresolver.strategy;

import com.utilityresolver.exception.InvalidBillException;
import com.utilityresolver.model.Share;
import com.utilityresolver.model.UsageRecord;

import java.util.ArrayList;
import java.util.List;

/** Each roommate pays according to the number of days they lived in the apartment. */
public class ProratedByDaysSplit extends AbstractSplitStrategy {

    public ProratedByDaysSplit() {
        super("PRORATED", "Prorated by days",
                "Each roommate pays for the share of 'person-days' they lived in the flat that month.");
    }

    @Override
    protected List<Share> computeShares(double amount, List<UsageRecord> participants, String unitLabel)
            throws InvalidBillException {
        int totalDays = 0;
        for (UsageRecord r : participants) {
            totalDays += r.getDaysPresent();
        }
        if (totalDays <= 0) {
            throw new InvalidBillException("Prorated split needs days present for at least one roommate");
        }
        List<Share> shares = new ArrayList<>();
        for (UsageRecord r : participants) {
            double fraction = (double) r.getDaysPresent() / totalDays;
            String why = r.getDaysPresent() + " of " + totalDays + " person-days (" + pct(fraction) + ") x " + money(amount);
            shares.add(new Share(r.getRoommateId(), amount * fraction, why));
        }
        return shares;
    }
}
