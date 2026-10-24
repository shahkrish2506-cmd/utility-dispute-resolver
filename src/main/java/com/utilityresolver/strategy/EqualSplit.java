package com.utilityresolver.strategy;

import com.utilityresolver.model.Share;
import com.utilityresolver.model.UsageRecord;

import java.util.ArrayList;
import java.util.List;

/** Everyone present during the month pays the same amount. */
public class EqualSplit extends AbstractSplitStrategy {

    public EqualSplit() {
        super("EQUAL", "Equal split", "Total divided equally among every roommate present in the month.");
    }

    @Override
    protected List<Share> computeShares(double amount, List<UsageRecord> participants, String unitLabel) {
        int n = participants.size();
        double each = amount / n;
        List<Share> shares = new ArrayList<>();
        for (UsageRecord r : participants) {
            shares.add(new Share(r.getRoommateId(), each, money(amount) + " / " + n + " people"));
        }
        return shares;
    }
}
