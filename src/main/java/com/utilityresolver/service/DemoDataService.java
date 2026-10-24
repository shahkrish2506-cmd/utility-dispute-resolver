package com.utilityresolver.service;

import com.utilityresolver.exception.AppException;
import com.utilityresolver.model.Bill;
import com.utilityresolver.model.Dispute;
import com.utilityresolver.model.Roommate;
import com.utilityresolver.model.Transfer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Loads a realistic three-month sample so the app can be demonstrated immediately.
 * Uses the normal services, so every validation rule is exercised.
 */
public class DemoDataService {
    private final AppContext ctx;

    public DemoDataService(AppContext ctx) {
        this.ctx = ctx;
    }

    public void load() throws AppException, IOException {
        ctx.store().wipe();
        ctx.store().getApartment().update("Sunrise Residency, Flat 302", "HSR Layout, Bengaluru");
        ctx.store().saveApartment();

        RoommateService rs = ctx.roommates();
        Roommate aarav = rs.add("Aarav", "aarav@example.com", "98450 11111", LocalDate.of(2026, 1, 1), null);
        Roommate diya = rs.add("Diya", "diya@example.com", "98450 22222", LocalDate.of(2026, 1, 1), null);
        Roommate kabir = rs.add("Kabir", "kabir@example.com", "98450 33333", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 10, 20));
        Roommate meera = rs.add("Meera", "meera@example.com", "98450 44444", LocalDate.of(2026, 9, 12), null);
        Roommate[] all = {aarav, diya, kabir, meera};

        // ---- August 2026: three roommates ----
        YearMonth aug = YearMonth.of(2026, 8);
        bill(aug, "ELECTRICITY", 2860, 300, aarav, "USAGE", all, new double[]{210, 95, 155, 0});
        bill(aug, "WATER", 780, 0, diya, "PRORATED", all, null);
        bill(aug, "GAS", 1140, 0, kabir, "EQUAL", all, null);
        bill(aug, "INTERNET", 999, 0, aarav, "PRORATED", all, null);
        bill(aug, "GROCERIES", 5200, 0, diya, "PRORATED", all, null, "Monthly groceries (DMart + vegetables)");
        bill(aug, "MISCELLANEOUS", 1200, 0, kabir, "EQUAL", all, null, "Cleaning supplies and new mop");
        settleFully(aug, LocalDate.of(2026, 9, 3));
        ctx.periods().finalizeMonth(aug);

        // ---- September 2026: Meera joins on the 12th ----
        YearMonth sep = YearMonth.of(2026, 9);
        Bill sepPower = bill(sep, "ELECTRICITY", 3240, 300, diya, "USAGE", all, new double[]{240, 110, 150, 85});
        bill(sep, "WATER", 840, 0, aarav, "PRORATED", all, null);
        bill(sep, "GAS", 1180, 0, meera, "PRORATED", all, null);
        bill(sep, "INTERNET", 999, 0, aarav, "PRORATED", all, null);
        bill(sep, "GROCERIES", 5850, 0, meera, "PRORATED", all, null, "Monthly groceries");
        bill(sep, "MISCELLANEOUS", 2400, 0, aarav, "EQUAL", all, null, "Plumber visit (kitchen sink)");
        Dispute d = ctx.disputes().raise(sepPower.getId(), kabir.getId(),
                "I was away for 6 days, my sub-meter reading looks too high.");
        ctx.disputes().changeStatus(d.getId(), "RESOLVED",
                "Re-checked meter photo: 150 kWh is correct. Bill stands.");
        settleFully(sep, LocalDate.of(2026, 10, 4));

        // ---- October 2026: Kabir moves out on the 20th ----
        YearMonth oct = YearMonth.of(2026, 10);
        Bill octPower = bill(oct, "ELECTRICITY", 3520, 300, aarav, "USAGE", all, new double[]{260, 120, 90, 145});
        bill(oct, "WATER", 910, 0, diya, "PRORATED", all, null);
        bill(oct, "GAS", 1210, 0, kabir, "EQUAL", all, null);
        bill(oct, "INTERNET", 1199, 0, aarav, "PRORATED", all, null);
        bill(oct, "GROCERIES", 6120, 0, diya, "PRORATED", all, null, "Monthly groceries + Diwali sweets");
        bill(oct, "MISCELLANEOUS", 1800, 0, meera, "EQUAL", all, null, "House help, October");
        ctx.payments().record(meera.getId(), diya.getId(), 500, LocalDate.of(2026, 10, 22), oct, "Part payment (UPI)");
        ctx.disputes().raise(octPower.getId(), meera.getId(),
                "My AC sub-meter shows 125 kWh, not 145. Please re-check the reading.");

        ctx.store().audit().log("DEMO_LOADED", "Sample data for Aug-Oct 2026");
    }

    private Bill bill(YearMonth month, String type, double total, double fixed, Roommate paidBy, String strategy,
                      Roommate[] people, double[] units) throws AppException, IOException {
        return bill(month, type, total, fixed, paidBy, strategy, people, units, "");
    }

    private Bill bill(YearMonth month, String type, double total, double fixed, Roommate paidBy, String strategy,
                      Roommate[] people, double[] units, String notes) throws AppException, IOException {
        BillRequest req = new BillRequest();
        req.utilityType = type;
        req.month = month;
        req.totalAmount = total;
        req.fixedCharge = fixed;
        req.paidById = paidBy.getId();
        req.strategyKey = strategy;
        req.dueDate = month.atDay(15).plusMonths(1);
        req.notes = notes;
        for (int i = 0; i < people.length; i++) {
            int days = people[i].daysPresentIn(month);
            if (days > 0) {
                req.usage.add(new BillRequest.UsageEntry(people[i].getId(), units == null ? 0 : units[i], days));
            }
        }
        return ctx.billing().create(req);
    }

    private void settleFully(YearMonth month, LocalDate date) throws AppException, IOException {
        List<Transfer> transfers = ctx.settlement().summarize(month).transfers;
        for (Transfer t : transfers) {
            ctx.payments().record(t.getFromId(), t.getToId(), t.getAmount(), date, month, "Settled via UPI");
        }
    }
}
