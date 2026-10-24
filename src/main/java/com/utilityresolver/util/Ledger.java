package com.utilityresolver.util;

import com.utilityresolver.model.Bill;
import com.utilityresolver.model.utility.Utility;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * GENERIC ledger that keeps the bill history of ONE utility type.
 * The bound {@code T extends Utility} means only Utility subclasses can be used,
 * e.g. {@code Ledger<Electricity>} or {@code Ledger<Water>}.
 */
public class Ledger<T extends Utility> {
    private final T utility;
    private final List<Bill> entries = new ArrayList<>();

    public Ledger(T utility) {
        this.utility = utility;
    }

    public void record(Bill bill) {
        if (bill.getUtility().getType() != utility.getType()) {
            throw new IllegalArgumentException("Bill " + bill.getId() + " is " + bill.getUtility()
                    + ", not " + utility);
        }
        entries.add(bill);
    }

    public T getUtility() {
        return utility;
    }

    public List<Bill> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public double totalFor(YearMonth month) {
        return entries.stream().filter(b -> b.getMonth().equals(month)).mapToDouble(Bill::getTotalAmount).sum();
    }

    public double unitsFor(YearMonth month) {
        return entries.stream().filter(b -> b.getMonth().equals(month)).mapToDouble(Bill::getTotalUnits).sum();
    }

    /** Month -> total amount, sorted by month (TreeMap keeps keys ordered). */
    public SortedMap<YearMonth, Double> monthlyTotals() {
        SortedMap<YearMonth, Double> map = new TreeMap<>();
        for (Bill b : entries) {
            map.merge(b.getMonth(), b.getTotalAmount(), Double::sum);
        }
        return map;
    }

    public double averageMonthly() {
        SortedMap<YearMonth, Double> totals = monthlyTotals();
        return totals.isEmpty() ? 0 : totals.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    public Optional<Bill> highestBill() {
        return entries.stream().max(Comparator.comparingDouble(Bill::getTotalAmount));
    }

    /** Percentage change versus the previous month (null if there is no previous month). */
    public Double changeVsPrevious(YearMonth month) {
        double now = totalFor(month);
        double before = totalFor(month.minusMonths(1));
        if (before == 0) {
            return null;
        }
        return (now - before) / before * 100.0;
    }
}
