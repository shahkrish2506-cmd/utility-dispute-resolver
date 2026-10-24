package com.utilityresolver.model.utility;

import com.utilityresolver.strategy.ProratedByDaysSplit;
import com.utilityresolver.strategy.SplitStrategy;

/** Internet: flat monthly plan, not metered per person. */
public class Internet extends Utility {

    public Internet() {
        super(UtilityType.INTERNET, "Internet", "GB");
    }

    @Override
    public boolean isMetered() {
        return false; // cannot use usage-based split
    }

    @Override
    public SplitStrategy getDefaultStrategy() {
        return new ProratedByDaysSplit();
    }

    @Override
    public String getBillingRule() {
        return "Flat plan: shared by days present (or equally). Usage-based split is not allowed.";
    }
}
