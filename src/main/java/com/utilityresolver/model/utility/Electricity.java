package com.utilityresolver.model.utility;

import com.utilityresolver.exception.InvalidBillException;
import com.utilityresolver.model.Bill;
import com.utilityresolver.model.Share;
import com.utilityresolver.strategy.ProratedByDaysSplit;
import com.utilityresolver.strategy.SplitStrategy;
import com.utilityresolver.strategy.UsageBasedSplit;
import com.utilityresolver.util.Money;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Electricity: a fixed/meter charge that everyone shares by days present,
 * plus an energy charge split by the chosen strategy (usually kWh used).
 */
public class Electricity extends Utility {

    public Electricity() {
        super(UtilityType.ELECTRICITY, "Electricity", "kWh");
    }

    @Override
    public boolean isMetered() {
        return true;
    }

    @Override
    public SplitStrategy getDefaultStrategy() {
        return new UsageBasedSplit();
    }

    @Override
    public String getBillingRule() {
        return "Fixed charge is shared by days present; the remaining energy charge uses the chosen rule (default: kWh used).";
    }

    /** OVERRIDDEN: two-part tariff (fixed part + variable part). */
    @Override
    public List<Share> calculateShares(Bill bill, SplitStrategy strategy) throws InvalidBillException {
        validate(bill, strategy);
        double fixed = bill.getFixedCharge();
        double variable = bill.getTotalAmount() - fixed;
        if (fixed <= 0) {
            return super.calculateShares(bill, strategy);
        }
        Map<String, Share> merged = new LinkedHashMap<>();
        for (Share s : new ProratedByDaysSplit().split(fixed, bill.getUsageRecords(), getUnitLabel())) {
            merged.put(s.getRoommateId(), new Share(s.getRoommateId(), s.getAmount(), "Fixed: " + s.getExplanation()));
        }
        if (variable > 0) {
            for (Share s : strategy.split(variable, bill.getUsageRecords(), getUnitLabel())) {
                Share energy = new Share(s.getRoommateId(), s.getAmount(), "Energy: " + s.getExplanation());
                Share existing = merged.get(s.getRoommateId());
                if (existing == null) {
                    merged.put(s.getRoommateId(), energy);
                } else {
                    existing.merge(energy);
                }
            }
        }
        return Money.balanceRounding(new ArrayList<>(merged.values()), bill.getTotalAmount());
    }
}
