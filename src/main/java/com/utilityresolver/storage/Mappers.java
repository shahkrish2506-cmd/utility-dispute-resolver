package com.utilityresolver.storage;

import com.utilityresolver.exception.AppException;
import com.utilityresolver.model.Bill;
import com.utilityresolver.model.Dispute;
import com.utilityresolver.model.DisputeStatus;
import com.utilityresolver.model.Payment;
import com.utilityresolver.model.Roommate;
import com.utilityresolver.model.UsageRecord;
import com.utilityresolver.model.utility.UtilityType;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** CSV mappers for each entity (static nested classes implementing the CsvMapper interface). */
public final class Mappers {
    private Mappers() {
    }

    static LocalDate date(String s) {
        return (s == null || s.isBlank()) ? null : LocalDate.parse(s.trim());
    }

    static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    static String get(String[] row, int i) {
        return i < row.length ? row[i] : "";
    }

    public static class RoommateMapper implements CsvMapper<Roommate> {
        @Override
        public String[] header() {
            return new String[]{"id", "name", "email", "phone", "joinDate", "leaveDate"};
        }

        @Override
        public String[] toRow(Roommate r) {
            return new String[]{r.getId(), r.getName(), r.getEmail(), r.getPhone(),
                    str(r.getJoinDate()), str(r.getLeaveDate())};
        }

        @Override
        public Roommate fromRow(String[] row) throws AppException {
            return new Roommate(get(row, 0), get(row, 1), get(row, 2), get(row, 3),
                    date(get(row, 4)), date(get(row, 5)));
        }
    }

    /** Usage records are stored inside the bill row as "R1:120.0:30;R2:80.0:30". */
    public static class BillMapper implements CsvMapper<Bill> {
        @Override
        public String[] header() {
            return new String[]{"id", "utility", "month", "totalAmount", "fixedCharge", "paidBy",
                    "strategy", "dueDate", "notes", "usage"};
        }

        @Override
        public String[] toRow(Bill b) {
            List<String> parts = new ArrayList<>();
            for (UsageRecord u : b.getUsageRecords()) {
                parts.add(u.getRoommateId() + ":" + u.getUnits() + ":" + u.getDaysPresent());
            }
            return new String[]{b.getId(), b.getUtility().getType().name(), b.getMonth().toString(),
                    String.valueOf(b.getTotalAmount()), String.valueOf(b.getFixedCharge()), b.getPaidById(),
                    b.getStrategyKey(), str(b.getDueDate()), b.getNotes(), String.join(";", parts)};
        }

        @Override
        public Bill fromRow(String[] row) throws AppException {
            Bill b = new Bill(get(row, 0), UtilityType.parse(get(row, 1)).create(), YearMonth.parse(get(row, 2)),
                    Double.parseDouble(get(row, 3)), Double.parseDouble(get(row, 4)), get(row, 5),
                    get(row, 6), date(get(row, 7)), get(row, 8));
            String usage = get(row, 9);
            if (!usage.isBlank()) {
                for (String part : usage.split(";")) {
                    String[] p = part.split(":");
                    b.addUsage(p[0], Double.parseDouble(p[1]), Integer.parseInt(p[2]));
                }
            }
            return b;
        }
    }

    public static class PaymentMapper implements CsvMapper<Payment> {
        @Override
        public String[] header() {
            return new String[]{"id", "from", "to", "amount", "date", "month", "note"};
        }

        @Override
        public String[] toRow(Payment p) {
            return new String[]{p.getId(), p.getFromId(), p.getToId(), String.valueOf(p.getAmount()),
                    str(p.getDate()), p.getMonth().toString(), p.getNote()};
        }

        @Override
        public Payment fromRow(String[] row) throws AppException {
            return new Payment(get(row, 0), get(row, 1), get(row, 2), Double.parseDouble(get(row, 3)),
                    date(get(row, 4)), YearMonth.parse(get(row, 5)), get(row, 6));
        }
    }

    public static class DisputeMapper implements CsvMapper<Dispute> {
        @Override
        public String[] header() {
            return new String[]{"id", "billId", "raisedBy", "reason", "raisedOn", "status", "resolution", "closedOn"};
        }

        @Override
        public String[] toRow(Dispute d) {
            return new String[]{d.getId(), d.getBillId(), d.getRaisedById(), d.getReason(), str(d.getRaisedOn()),
                    d.getStatus().name(), d.getResolutionNote(), str(d.getClosedOn())};
        }

        @Override
        public Dispute fromRow(String[] row) throws AppException {
            return new Dispute(get(row, 0), get(row, 1), get(row, 2), get(row, 3), date(get(row, 4)),
                    DisputeStatus.valueOf(get(row, 5)), get(row, 6), date(get(row, 7)));
        }
    }
}
