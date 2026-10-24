package com.utilityresolver.model.utility;

import com.utilityresolver.strategy.ProratedByDaysSplit;
import com.utilityresolver.strategy.SplitStrategy;

/**
 * Food / groceries bought for the whole flat (rice, milk, vegetables, cooking oil...).
 * Not metered per person; by default shared by the days each roommate was present,
 * because someone who was away for a week ate less. Anyone who opted out is unticked.
 */
public class Groceries extends Utility {

    public Groceries() {
        super(UtilityType.GROCERIES, "Food / Groceries", "items");
    }

    @Override
    public boolean isMetered() {
        return false; // nobody weighs what each person ate
    }

    @Override
    public SplitStrategy getDefaultStrategy() {
        return new ProratedByDaysSplit();
    }

    @Override
    public String getBillingRule() {
        return "Shared food: split by days present (or equally). Untick anyone who opted out of the shared groceries.";
    }
}
