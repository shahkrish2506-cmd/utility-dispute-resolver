package com.utilityresolver.model.utility;

import com.utilityresolver.exception.InvalidBillException;
import com.utilityresolver.model.Bill;
import com.utilityresolver.model.Share;
import com.utilityresolver.strategy.SplitStrategy;
import com.utilityresolver.util.Money;

import java.util.List;

/**
 * ABSTRACT base class for every utility (Electricity, Water, Gas, Internet).
 * Subclasses decide their unit, default split rule and may OVERRIDE
 * calculateShares() to apply their own billing logic (POLYMORPHISM).
 */
public abstract class Utility {
    private final UtilityType type;
    private final String displayName;
    private final String unitLabel;

    protected Utility(UtilityType type, String displayName, String unitLabel) {
        this.type = type;
        this.displayName = displayName;
        this.unitLabel = unitLabel;
    }

    /** Whether individual consumption can be measured (sub-meter / reading). */
    public abstract boolean isMetered();

    /** The split rule that is fairest for this utility by default. */
    public abstract SplitStrategy getDefaultStrategy();

    /** Plain-English description of how this utility is billed. */
    public abstract String getBillingRule();

    /**
     * Splits a bill among roommates. Default behaviour: hand the whole amount to the
     * chosen strategy. Subclasses override this when their billing has extra rules.
     */
    public List<Share> calculateShares(Bill bill, SplitStrategy strategy) throws InvalidBillException {
        validate(bill, strategy);
        List<Share> shares = strategy.split(bill.getTotalAmount(), bill.getUsageRecords(), unitLabel);
        return Money.balanceRounding(shares, bill.getTotalAmount());
    }

    /** Common checks shared by all utilities. */
    protected void validate(Bill bill, SplitStrategy strategy) throws InvalidBillException {
        if (strategy.requiresUsage() && !isMetered()) {
            throw new InvalidBillException(displayName + " is not metered per person, so a usage-based split "
                    + "is not possible. Use equal or prorated split.");
        }
        boolean anyParticipant = bill.getUsageRecords().stream().anyMatch(r -> r.isParticipant());
        if (!anyParticipant) {
            throw new InvalidBillException("Bill " + bill.getId() + " has no participating roommates");
        }
    }

    public UtilityType getType() { return type; }

    public String getDisplayName() { return displayName; }

    public String getUnitLabel() { return unitLabel; }

    @Override
    public String toString() {
        return displayName;
    }
}
