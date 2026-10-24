package com.utilityresolver.service;

import com.utilityresolver.model.Bill;
import com.utilityresolver.model.Dispute;
import com.utilityresolver.model.Payment;
import com.utilityresolver.model.RoommateBalance;
import com.utilityresolver.model.Share;
import com.utilityresolver.model.Transfer;
import com.utilityresolver.model.utility.UtilityType;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything needed to show / print the settlement for one month. */
public class MonthSummary {
    public final YearMonth month;
    public final boolean finalized;
    public final List<Bill> bills = new ArrayList<>();
    public final Map<String, List<Share>> sharesByBill = new LinkedHashMap<>();
    public final Map<String, String> billErrors = new LinkedHashMap<>();
    public final Map<UtilityType, Double> totalsByUtility = new EnumMap<>(UtilityType.class);
    public final List<RoommateBalance> balances = new ArrayList<>();
    public final List<Payment> payments = new ArrayList<>();
    public final List<Dispute> disputes = new ArrayList<>();
    public final List<Transfer> transfers = new ArrayList<>();
    public double grandTotal;

    public MonthSummary(YearMonth month, boolean finalized) {
        this.month = month;
        this.finalized = finalized;
    }

    public long unresolvedDisputes() {
        return disputes.stream().filter(d -> d.getStatus().isUnresolved()).count();
    }

    public boolean isSettled() {
        return transfers.isEmpty();
    }
}
