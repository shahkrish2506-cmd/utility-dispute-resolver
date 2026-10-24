package com.utilityresolver.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Plain data object carrying the fields of the "Add / Edit bill" form. */
public class BillRequest {
    public String utilityType;
    public YearMonth month;
    public double totalAmount;
    public double fixedCharge;
    public String paidById;
    public String strategyKey;
    public LocalDate dueDate;
    public String notes;
    public final List<UsageEntry> usage = new ArrayList<>();

    /** One row of the usage table in the form. */
    public static class UsageEntry {
        public final String roommateId;
        public final double units;
        public final int days;

        public UsageEntry(String roommateId, double units, int days) {
            this.roommateId = roommateId;
            this.units = units;
            this.days = days;
        }
    }
}
