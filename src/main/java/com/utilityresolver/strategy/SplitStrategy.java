package com.utilityresolver.strategy;

import com.utilityresolver.exception.InvalidBillException;
import com.utilityresolver.model.Share;
import com.utilityresolver.model.UsageRecord;

import java.util.List;

/**
 * INTERFACE for every bill-splitting rule (Strategy design pattern).
 * New rules can be added without touching the Utility or Bill classes.
 */
public interface SplitStrategy {

    /** Short machine key, e.g. "EQUAL". */
    String getKey();

    String getDisplayName();

    String getDescription();

    /** True if the rule needs per-person usage units (meter readings). */
    boolean requiresUsage();

    /**
     * Splits {@code amount} among the roommates in {@code records}.
     *
     * @param unitLabel unit used only for the explanation text (kWh, litres...)
     */
    List<Share> split(double amount, List<UsageRecord> records, String unitLabel) throws InvalidBillException;
}
