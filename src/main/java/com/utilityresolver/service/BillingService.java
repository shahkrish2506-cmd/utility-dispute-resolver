package com.utilityresolver.service;

import com.utilityresolver.exception.AppException;
import com.utilityresolver.exception.InvalidBillException;
import com.utilityresolver.exception.ValidationException;
import com.utilityresolver.model.Bill;
import com.utilityresolver.model.Dispute;
import com.utilityresolver.model.Roommate;
import com.utilityresolver.model.Share;
import com.utilityresolver.model.utility.Utility;
import com.utilityresolver.model.utility.UtilityType;
import com.utilityresolver.storage.DataStore;
import com.utilityresolver.strategy.SplitStrategy;
import com.utilityresolver.strategy.StrategyFactory;
import com.utilityresolver.util.Ledger;
import com.utilityresolver.util.Money;

import java.io.IOException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Creates, edits and splits bills. */
public class BillingService {
    private final DataStore store;
    private final RoommateService roommates;
    private final PeriodService periods;

    public BillingService(DataStore store, RoommateService roommates, PeriodService periods) {
        this.store = store;
        this.roommates = roommates;
        this.periods = periods;
    }

    public List<Bill> forMonth(YearMonth month) {
        return store.bills().findWhere(b -> b.getMonth().equals(month)).stream()
                .sorted(Comparator.comparing((Bill b) -> b.getUtility().getType()).thenComparing(Bill::getId))
                .collect(Collectors.toList());
    }

    public List<Bill> all() {
        return store.bills().findAll();
    }

    public Bill get(String id) throws AppException {
        return store.bills().getOrThrow(id);
    }

    /** Every month that has data, newest first. */
    public List<YearMonth> monthsWithData() {
        Set<YearMonth> months = new TreeSet<>(Comparator.reverseOrder());
        store.bills().findAll().forEach(b -> months.add(b.getMonth()));
        store.payments().findAll().forEach(p -> months.add(p.getMonth()));
        return new ArrayList<>(months);
    }

    /** Splits the bill using its stored strategy (polymorphic call on the Utility). */
    public List<Share> calculateShares(Bill bill) throws AppException {
        SplitStrategy strategy = StrategyFactory.get(bill.getStrategyKey());
        return bill.getUtility().calculateShares(bill, strategy);
    }

    public Bill create(BillRequest req) throws AppException, IOException {
        Utility utility = parseUtility(req.utilityType);
        periods.ensureEditable(req.month);
        Bill bill = new Bill(store.billIds().next(), utility, req.month, req.totalAmount, req.fixedCharge,
                req.paidById, req.strategyKey, req.dueDate, req.notes);
        fillUsage(bill, req);
        calculateShares(bill); // throws InvalidBillException before saving if the split is impossible
        store.bills().save(bill);
        store.audit().log("BILL_ADDED", bill.getId() + " " + utility + " " + req.month + " "
                + Money.format(req.totalAmount) + " paid by " + roommates.nameOf(req.paidById)
                + " [" + bill.getStrategyKey() + "]");
        return bill;
    }

    /** Calculates shares for a bill that has not been saved yet (live preview in the form). */
    public List<Share> preview(BillRequest req) throws AppException {
        Utility utility = parseUtility(req.utilityType);
        Bill trial = new Bill("PREVIEW", utility, req.month, req.totalAmount, req.fixedCharge,
                req.paidById, req.strategyKey, req.dueDate, req.notes);
        fillUsage(trial, req);
        return calculateShares(trial);
    }

    public Bill update(String id, BillRequest req) throws AppException, IOException {
        Bill bill = get(id);
        periods.ensureEditable(bill.getMonth());
        periods.ensureEditable(req.month);
        if (req.utilityType != null && !req.utilityType.isBlank()
                && UtilityType.parse(req.utilityType) != bill.getUtility().getType()) {
            throw new ValidationException("Utility type of an existing bill cannot be changed; delete and re-add it");
        }
        // Validate on a scratch copy first so a failed edit leaves the real bill untouched.
        Bill trial = new Bill(bill.getId(), bill.getUtility(), req.month, req.totalAmount, req.fixedCharge,
                req.paidById, req.strategyKey, req.dueDate, req.notes);
        fillUsage(trial, req);
        calculateShares(trial);
        bill.update(req.month, req.totalAmount, req.fixedCharge, req.paidById, req.strategyKey, req.dueDate, req.notes);
        bill.clearUsage();
        fillUsage(bill, req);
        store.bills().flush();
        store.audit().log("BILL_UPDATED", bill.getId() + " " + bill.getUtility() + " " + bill.getMonth() + " "
                + Money.format(bill.getTotalAmount()) + " [" + bill.getStrategyKey() + "]");
        return bill;
    }

    public void delete(String id) throws AppException, IOException {
        Bill bill = get(id);
        periods.ensureEditable(bill.getMonth());
        for (Dispute d : store.disputes().findWhere(d -> d.getBillId().equals(id))) {
            store.disputes().delete(d.getId());
        }
        store.bills().delete(id);
        store.audit().log("BILL_DELETED", id + " " + bill.getUtility() + " " + bill.getMonth());
    }

    private void fillUsage(Bill bill, BillRequest req) throws AppException {
        roommates.get(req.paidById); // must exist
        if (req.usage.isEmpty()) {
            throw new InvalidBillException("Add at least one roommate to the bill");
        }
        for (BillRequest.UsageEntry u : req.usage) {
            Roommate r = roommates.get(u.roommateId);
            int maxDays = req.month.lengthOfMonth();
            if (u.days > maxDays) {
                throw new ValidationException(r.getName() + ": days present cannot exceed " + maxDays);
            }
            bill.addUsage(u.roommateId, u.units, u.days);
        }
    }

    private Utility parseUtility(String type) throws ValidationException {
        try {
            return UtilityType.parse(type).create();
        } catch (RuntimeException e) {
            throw new ValidationException("Unknown utility type: " + type);
        }
    }

    /** GENERIC METHOD: builds a ledger for any Utility subclass. */
    public <T extends Utility> Ledger<T> buildLedger(T utility) {
        Ledger<T> ledger = new Ledger<>(utility);
        for (Bill b : store.bills().findAll()) {
            if (b.getUtility().getType() == utility.getType()) {
                ledger.record(b);
            }
        }
        return ledger;
    }

    /** One ledger per utility type (EnumMap = fast map keyed by enum). */
    public Map<UtilityType, Ledger<Utility>> allLedgers() {
        Map<UtilityType, Ledger<Utility>> map = new EnumMap<>(UtilityType.class);
        for (UtilityType t : UtilityType.values()) {
            map.put(t, buildLedger(t.create()));
        }
        return map;
    }
}
