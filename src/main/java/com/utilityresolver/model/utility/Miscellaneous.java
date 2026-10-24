package com.utilityresolver.model.utility;

import com.utilityresolver.exception.InvalidBillException;
import com.utilityresolver.model.Bill;
import com.utilityresolver.strategy.EqualSplit;
import com.utilityresolver.strategy.SplitStrategy;

/**
 * Any other shared expense: cleaning supplies, a plumber, a maid, a new mop...
 * Because the category is open-ended, a short description is REQUIRED so that
 * everyone knows what they are paying for.
 */
public class Miscellaneous extends Utility {

    public Miscellaneous() {
        super(UtilityType.MISCELLANEOUS, "Miscellaneous", "items");
    }

    @Override
    public boolean isMetered() {
        return false;
    }

    @Override
    public SplitStrategy getDefaultStrategy() {
        return new EqualSplit();
    }

    @Override
    public String getBillingRule() {
        return "Other shared costs (cleaning, repairs, maid): split equally by default. A description is required.";
    }

    /** OVERRIDDEN: adds a rule only this category has, then runs the common checks. */
    @Override
    protected void validate(Bill bill, SplitStrategy strategy) throws InvalidBillException {
        if (bill.getNotes() == null || bill.getNotes().trim().length() < 3) {
            throw new InvalidBillException("Miscellaneous expenses need a description in Notes (e.g. 'Plumber visit')");
        }
        super.validate(bill, strategy);
    }
}
