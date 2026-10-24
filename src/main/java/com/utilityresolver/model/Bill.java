package com.utilityresolver.model;

import com.utilityresolver.exception.ValidationException;
import com.utilityresolver.model.utility.Utility;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A monthly utility bill. COMPOSITION: the bill creates and owns its UsageRecords
 * (they are meaningless without the bill). ASSOCIATION: it refers to the Utility type
 * and to the roommate who paid the provider.
 */
public class Bill implements Identifiable {
    private final String id;
    private final Utility utility;
    private YearMonth month;
    private double totalAmount;
    private double fixedCharge;
    private String paidById;
    private String strategyKey;
    private LocalDate dueDate;
    private String notes;
    private final List<UsageRecord> usageRecords = new ArrayList<>();

    public Bill(String id, Utility utility, YearMonth month, double totalAmount, double fixedCharge,
                String paidById, String strategyKey, LocalDate dueDate, String notes) throws ValidationException {
        if (utility == null) {
            throw new ValidationException("Utility type is required");
        }
        this.id = id;
        this.utility = utility;
        update(month, totalAmount, fixedCharge, paidById, strategyKey, dueDate, notes);
    }

    public final void update(YearMonth month, double totalAmount, double fixedCharge, String paidById,
                             String strategyKey, LocalDate dueDate, String notes) throws ValidationException {
        if (month == null) {
            throw new ValidationException("Billing month is required");
        }
        if (totalAmount <= 0) {
            throw new ValidationException("Bill amount must be greater than zero");
        }
        if (fixedCharge < 0) {
            throw new ValidationException("Fixed charge cannot be negative");
        }
        if (fixedCharge > totalAmount) {
            throw new ValidationException("Fixed charge cannot be more than the total bill");
        }
        if (paidById == null || paidById.isBlank()) {
            throw new ValidationException("Select who paid this bill to the provider");
        }
        this.month = month;
        this.totalAmount = totalAmount;
        this.fixedCharge = fixedCharge;
        this.paidById = paidById;
        this.strategyKey = (strategyKey == null || strategyKey.isBlank())
                ? utility.getDefaultStrategy().getKey() : strategyKey;
        this.dueDate = dueDate;
        this.notes = notes == null ? "" : notes.trim();
    }

    /** The bill itself creates the UsageRecord objects (composition). */
    public void addUsage(String roommateId, double units, int daysPresent) throws ValidationException {
        for (UsageRecord r : usageRecords) {
            if (r.getRoommateId().equals(roommateId)) {
                throw new ValidationException("Usage for roommate " + roommateId + " entered twice");
            }
        }
        usageRecords.add(new UsageRecord(roommateId, units, daysPresent));
    }

    public void clearUsage() {
        usageRecords.clear();
    }

    public List<UsageRecord> getUsageRecords() {
        return Collections.unmodifiableList(usageRecords);
    }

    public double getTotalUnits() {
        double sum = 0;
        for (UsageRecord r : usageRecords) {
            sum += r.getUnits();
        }
        return sum;
    }

    public boolean involves(String roommateId) {
        if (paidById.equals(roommateId)) {
            return true;
        }
        return usageRecords.stream().anyMatch(r -> r.getRoommateId().equals(roommateId) && r.isParticipant());
    }

    @Override
    public String getId() { return id; }

    public Utility getUtility() { return utility; }

    public YearMonth getMonth() { return month; }

    public double getTotalAmount() { return totalAmount; }

    public double getFixedCharge() { return fixedCharge; }

    public String getPaidById() { return paidById; }

    public String getStrategyKey() { return strategyKey; }

    public LocalDate getDueDate() { return dueDate; }

    public String getNotes() { return notes; }
}
