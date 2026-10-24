package com.utilityresolver.service;

import com.utilityresolver.model.Apartment;
import com.utilityresolver.model.Bill;
import com.utilityresolver.model.Dispute;
import com.utilityresolver.model.Payment;
import com.utilityresolver.model.RoommateBalance;
import com.utilityresolver.model.Share;
import com.utilityresolver.model.Transfer;
import com.utilityresolver.model.utility.UtilityType;
import com.utilityresolver.storage.DataStore;
import com.utilityresolver.strategy.StrategyFactory;
import com.utilityresolver.util.CsvUtil;
import com.utilityresolver.util.Money;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds the monthly settlement report and WRITES it to text and CSV files. */
public class ReportService {
    private static final String LINE = "=".repeat(78);
    private static final String THIN = "-".repeat(78);
    private final DataStore store;
    private final SettlementService settlement;
    private final RoommateService roommates;

    public ReportService(DataStore store, SettlementService settlement, RoommateService roommates) {
        this.store = store;
        this.settlement = settlement;
        this.roommates = roommates;
    }

    public static String monthLabel(YearMonth m) {
        return m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.getYear();
    }

    public String buildText(YearMonth month) {
        MonthSummary s = settlement.summarize(month);
        Apartment apt = store.getApartment();
        StringBuilder sb = new StringBuilder();
        sb.append(LINE).append('\n');
        sb.append("  SHARED APARTMENT UTILITY SETTLEMENT REPORT\n");
        sb.append("  Apartment : ").append(apt.getName());
        if (!apt.getAddress().isEmpty()) {
            sb.append(", ").append(apt.getAddress());
        }
        sb.append('\n');
        sb.append("  Month     : ").append(monthLabel(month))
                .append("    Status: ").append(s.finalized ? "FINALISED" : "DRAFT (not yet finalised)").append('\n');
        sb.append("  Generated : ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")))
                .append('\n');
        sb.append(LINE).append("\n\n");

        sb.append("1. BILLS AND HOW THEY WERE SPLIT\n").append(THIN).append('\n');
        if (s.bills.isEmpty()) {
            sb.append("   No bills recorded for this month.\n");
        }
        for (Bill b : s.bills) {
            String rule;
            try {
                rule = StrategyFactory.get(b.getStrategyKey()).getDisplayName();
            } catch (Exception e) {
                rule = b.getStrategyKey();
            }
            sb.append(String.format("   [%s] %-12s %14s   paid by %-12s rule: %s%n", b.getId(),
                    b.getUtility().getDisplayName(), Money.format(b.getTotalAmount()),
                    roommates.nameOf(b.getPaidById()), rule));
            if (b.getFixedCharge() > 0) {
                sb.append("        includes fixed charge ").append(Money.format(b.getFixedCharge())).append('\n');
            }
            if (s.billErrors.containsKey(b.getId())) {
                sb.append("        ERROR: ").append(s.billErrors.get(b.getId())).append('\n');
                continue;
            }
            for (Share sh : s.sharesByBill.getOrDefault(b.getId(), List.of())) {
                sb.append(String.format("        - %-12s %12s   %s%n", roommates.nameOf(sh.getRoommateId()),
                        Money.format(sh.getAmount()), sh.getExplanation()));
            }
        }
        sb.append(String.format("%n   TOTAL SHARED BILLS THIS MONTH: %s%n%n", Money.format(s.grandTotal)));

        sb.append("2. ROOMMATE SUMMARY\n").append(THIN).append('\n');
        sb.append(String.format("   %-12s %5s %13s %13s %11s %11s %13s%n", "Roommate", "Days", "Fair share",
                "Paid bills", "Sent", "Received", "Net"));
        for (RoommateBalance b : s.balances) {
            sb.append(String.format("   %-12s %5d %13s %13s %11s %11s %13s%n", b.getRoommate().getName(),
                    b.getRoommate().daysPresentIn(month), Money.format(b.getTotalShare()),
                    Money.format(b.getPaidToProvider()), Money.format(b.getPaymentsSent()),
                    Money.format(b.getPaymentsReceived()), signed(b.getNet())));
        }
        sb.append("   (Net > 0: others owe this roommate.  Net < 0: this roommate still owes.)\n\n");

        sb.append("3. SETTLE UP\n").append(THIN).append('\n');
        if (s.transfers.isEmpty()) {
            sb.append("   Everyone is settled for this month.\n");
        }
        for (Transfer t : s.transfers) {
            sb.append(String.format("   %-12s pays  %-12s %12s%n", roommates.nameOf(t.getFromId()),
                    roommates.nameOf(t.getToId()), Money.format(t.getAmount())));
        }
        sb.append('\n');

        sb.append("4. PAYMENTS RECORDED\n").append(THIN).append('\n');
        if (s.payments.isEmpty()) {
            sb.append("   None.\n");
        }
        for (Payment p : s.payments) {
            sb.append(String.format("   %s  %-12s -> %-12s %12s  %s%n", p.getDate(), roommates.nameOf(p.getFromId()),
                    roommates.nameOf(p.getToId()), Money.format(p.getAmount()), p.getNote()));
        }
        sb.append('\n');

        sb.append("5. DISPUTES\n").append(THIN).append('\n');
        if (s.disputes.isEmpty()) {
            sb.append("   No disputes raised.\n");
        }
        for (Dispute d : s.disputes) {
            sb.append(String.format("   [%s] %-12s on %s by %s: %s%n", d.getId(), d.getStatus().getLabel(),
                    d.getBillId(), roommates.nameOf(d.getRaisedById()), d.getReason()));
            if (!d.getResolutionNote().isEmpty()) {
                sb.append("        Note: ").append(d.getResolutionNote()).append('\n');
            }
        }
        if (s.unresolvedDisputes() > 0) {
            sb.append("\n   WARNING: ").append(s.unresolvedDisputes())
                    .append(" unresolved dispute(s). This month cannot be finalised yet.\n");
        }
        sb.append('\n').append(LINE).append('\n');
        sb.append("  Generated by Shared Apartment Utility & Dispute Resolver\n");
        sb.append(LINE).append('\n');
        return sb.toString();
    }

    public String buildCsv(YearMonth month) {
        MonthSummary s = settlement.summarize(month);
        StringBuilder sb = new StringBuilder();
        // one column per category, generated from the enum so new categories appear automatically
        List<String> header = new ArrayList<>(List.of("Roommate", "Days Present"));
        for (UtilityType t : UtilityType.values()) {
            header.add(t.create().getDisplayName());
        }
        header.addAll(List.of("Fair Share", "Paid To Provider", "Payments Sent", "Payments Received", "Net Balance"));
        sb.append(CsvUtil.toLine(header.toArray(new String[0]))).append('\n');
        for (RoommateBalance b : s.balances) {
            Map<UtilityType, Double> perUtility = new EnumMap<>(UtilityType.class);
            for (Bill bill : s.bills) {
                for (Share sh : s.sharesByBill.getOrDefault(bill.getId(), List.of())) {
                    if (sh.getRoommateId().equals(b.getRoommate().getId())) {
                        perUtility.merge(bill.getUtility().getType(), sh.getAmount(), Double::sum);
                    }
                }
            }
            List<String> row = new ArrayList<>(List.of(b.getRoommate().getName(),
                    String.valueOf(b.getRoommate().daysPresentIn(month))));
            for (UtilityType t : UtilityType.values()) {
                row.add(Money.plain(perUtility.getOrDefault(t, 0.0)));
            }
            row.addAll(List.of(Money.plain(b.getTotalShare()), Money.plain(b.getPaidToProvider()),
                    Money.plain(b.getPaymentsSent()), Money.plain(b.getPaymentsReceived()),
                    Money.plain(b.getNet())));
            sb.append(CsvUtil.toLine(row.toArray(new String[0]))).append('\n');
        }
        List<String> total = new ArrayList<>(List.of("TOTAL", ""));
        for (UtilityType t : UtilityType.values()) {
            total.add(Money.plain(s.totalsByUtility.getOrDefault(t, 0.0)));
        }
        total.addAll(List.of(Money.plain(s.grandTotal), "", "", "", ""));
        sb.append(CsvUtil.toLine(total.toArray(new String[0]))).append('\n');
        sb.append('\n').append(CsvUtil.toLine("Settle up: from", "to", "amount")).append('\n');
        for (Transfer t : s.transfers) {
            sb.append(CsvUtil.toLine(roommates.nameOf(t.getFromId()), roommates.nameOf(t.getToId()),
                    Money.plain(t.getAmount()))).append('\n');
        }
        return sb.toString();
    }

    /** Writes settlement_YYYY-MM.txt and settlement_YYYY-MM.csv into the reports folder. */
    public Path[] export(YearMonth month) throws IOException {
        Files.createDirectories(store.getReportsDir());
        Path txt = store.getReportsDir().resolve("settlement_" + month + ".txt");
        Path csv = store.getReportsDir().resolve("settlement_" + month + ".csv");
        try (BufferedWriter w = Files.newBufferedWriter(txt, StandardCharsets.UTF_8)) {
            w.write(buildText(month));
        }
        try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
            w.write('﻿'); // BOM so Excel shows the rupee sign / unicode names correctly
            w.write(buildCsv(month));
        }
        store.audit().log("REPORT_EXPORTED", txt.getFileName() + ", " + csv.getFileName());
        return new Path[]{txt, csv};
    }

    private static String signed(double v) {
        double r = Money.round2(v);
        if (Math.abs(r) < 0.005) {
            return Money.format(0);
        }
        return (r > 0 ? "+" : "-") + Money.format(Math.abs(r));
    }
}
