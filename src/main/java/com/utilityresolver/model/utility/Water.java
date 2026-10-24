package com.utilityresolver.model.utility;

import com.utilityresolver.strategy.ProratedByDaysSplit;
import com.utilityresolver.strategy.SplitStrategy;

/** Water: usually one common meter, so it is shared by days each person lived there. */
public class Water extends Utility {

    public Water() {
        super(UtilityType.WATER, "Water", "litres");
    }

    @Override
    public boolean isMetered() {
        return true; // allowed if the flat has individual readings
    }

    @Override
    public SplitStrategy getDefaultStrategy() {
        return new ProratedByDaysSplit();
    }

    @Override
    public String getBillingRule() {
        return "Shared by number of days each roommate lived in the flat during the month.";
    }
}
