package com.utilityresolver.model;

import com.utilityresolver.exception.ValidationException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * A roommate living in the apartment. Tracks occupancy (move-in / move-out)
 * so the system can work out how many days of a month they actually lived there.
 */
public class Roommate extends Person {
    private LocalDate joinDate;
    private LocalDate leaveDate; // null = still living here

    public Roommate(String id, String name, String email, String phone,
                    LocalDate joinDate, LocalDate leaveDate) throws ValidationException {
        super(id, name, email, phone);
        setOccupancy(joinDate, leaveDate);
    }

    /** Convenience constructor for a roommate who is still living in the apartment. */
    public Roommate(String id, String name, LocalDate joinDate) throws ValidationException {
        this(id, name, "", "", joinDate, null);
    }

    @Override
    public String getRole() {
        return "Roommate";
    }

    public final void setOccupancy(LocalDate joinDate, LocalDate leaveDate) throws ValidationException {
        if (joinDate == null) {
            throw new ValidationException("Move-in date is required");
        }
        if (leaveDate != null && leaveDate.isBefore(joinDate)) {
            throw new ValidationException("Move-out date cannot be before move-in date");
        }
        this.joinDate = joinDate;
        this.leaveDate = leaveDate;
    }

    public LocalDate getJoinDate() { return joinDate; }

    public LocalDate getLeaveDate() { return leaveDate; }

    public boolean isActiveOn(LocalDate date) {
        return !date.isBefore(joinDate) && (leaveDate == null || !date.isAfter(leaveDate));
    }

    public boolean isCurrentlyActive() {
        return isActiveOn(LocalDate.now());
    }

    /** Number of days this roommate lived in the apartment during the given month. */
    public int daysPresentIn(YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        LocalDate start = joinDate.isAfter(first) ? joinDate : first;
        LocalDate end = (leaveDate != null && leaveDate.isBefore(last)) ? leaveDate : last;
        if (end.isBefore(start)) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(start, end) + 1;
    }
}
