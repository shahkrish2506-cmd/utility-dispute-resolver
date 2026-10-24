package com.utilityresolver.model.utility;

import com.utilityresolver.exception.InvalidBillException;
import com.utilityresolver.model.Bill;
import com.utilityresolver.model.Share;
import com.utilityresolver.strategy.EqualSplit;
import com.utilityresolver.strategy.SplitStrategy;

import java.util.List;

/** Gas (piped / cylinder): mostly used for shared cooking, so equal split by default. */
public class Gas extends Utility {

    public Gas() {
        super(UtilityType.GAS, "Gas", "SCM");
    }

    @Override
    public boolean isMetered() {
        return true;
    }

    @Override
    public SplitStrategy getDefaultStrategy() {
        return new EqualSplit();
    }

    @Override
    public String getBillingRule() {
        return "Shared cooking fuel: split equally among roommates present, unless individual usage is recorded.";
    }

    /** OVERRIDDEN: cylinder refills have no meter reading, so fall back to equal split if no units were entered. */
    @Override
    public List<Share> calculateShares(Bill bill, SplitStrategy strategy) throws InvalidBillException {
        if (strategy.requiresUsage() && bill.getTotalUnits() == 0) {
            return super.calculateShares(bill, new EqualSplit());
        }
        return super.calculateShares(bill, strategy);
    }
}
