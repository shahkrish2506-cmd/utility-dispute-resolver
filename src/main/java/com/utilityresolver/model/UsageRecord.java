package com.utilityresolver.model;

import com.utilityresolver.exception.NegativeUsageException;
import com.utilityresolver.exception.ValidationException;

/**
 * One roommate's consumption for one bill: units used (kWh, litres, ...) and
 * days present. Part of a Bill (COMPOSITION: it is created by, and dies with, its Bill).
 */
public class UsageRecord {
    private final String roommateId;
    private final double units;
    private final int daysPresent;

    public UsageRecord(String roommateId, double units, int daysPresent) throws ValidationException {
        if (roommateId == null || roommateId.isBlank()) {
            throw new ValidationException("Usage record needs a roommate");
        }
        if (units < 0) {
            throw new NegativeUsageException("Usage units", units);
        }
        if (daysPresent < 0) {
            throw new NegativeUsageException("Days present", daysPresent);
        }
        if (daysPresent > 31) {
            throw new ValidationException("Days present cannot exceed 31");
        }
        this.roommateId = roommateId;
        this.units = units;
        this.daysPresent = daysPresent;
    }

    public String getRoommateId() { return roommateId; }

    public double getUnits() { return units; }

    public int getDaysPresent() { return daysPresent; }

    public boolean isParticipant() { return daysPresent > 0 || units > 0; }
}
