package com.utilityresolver;

import com.utilityresolver.exception.InvalidBillException;
import com.utilityresolver.exception.MonthLockedException;
import com.utilityresolver.exception.NegativeUsageException;
import com.utilityresolver.exception.UnresolvedDisputeException;
import com.utilityresolver.exception.ValidationException;
import com.utilityresolver.model.Bill;
import com.utilityresolver.model.Dispute;
import com.utilityresolver.model.Roommate;
import com.utilityresolver.model.Share;
import com.utilityresolver.model.Transfer;
import com.utilityresolver.model.UsageRecord;
import com.utilityresolver.model.utility.Electricity;
import com.utilityresolver.model.utility.Groceries;
import com.utilityresolver.model.utility.Internet;
import com.utilityresolver.model.utility.Miscellaneous;
import com.utilityresolver.model.utility.Utility;
import com.utilityresolver.model.utility.Water;
import com.utilityresolver.service.AppContext;
import com.utilityresolver.service.BillRequest;
import com.utilityresolver.service.MonthSummary;
import com.utilityresolver.strategy.EqualSplit;
import com.utilityresolver.strategy.ProratedByDaysSplit;
import com.utilityresolver.strategy.UsageBasedSplit;
import com.utilityresolver.util.CsvUtil;
import com.utilityresolver.util.Json;
import com.utilityresolver.util.Ledger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Self-contained test suite (no JUnit needed). Run with test.sh / test.bat.
 * Each test is a method; a failed check throws AssertionError.
 */
public class AllTests {
    private static int passed;
    private static int failed;

    interface TestCase {
        void run() throws Exception;
    }

    public static void main(String[] args) throws Exception {
        test("Equal split divides total equally", AllTests::equalSplit);
        test("Usage-based split is proportional to units", AllTests::usageSplit);
        test("Prorated split uses days present", AllTests::proratedSplit);
        test("Shares always add up to the bill total (rounding)", AllTests::roundingAddsUp);
        test("Electricity splits fixed charge by days, energy by usage", AllTests::electricityTwoPart);
        test("Internet rejects usage-based split (polymorphism)", AllTests::internetRejectsUsage);
        test("Groceries default to days present and reject usage split", AllTests::groceries);
        test("Miscellaneous needs a description, then splits equally", AllTests::miscellaneous);
        test("Negative usage throws NegativeUsageException", AllTests::negativeUsage);
        test("Usage split with zero total units throws InvalidBillException", AllTests::zeroUnits);
        test("Bill validation: fixed charge > total is rejected", AllTests::fixedTooBig);
        test("Roommate days present for move-in/move-out months", AllTests::daysPresent);
        test("Roommate cannot move out before moving in", AllTests::badOccupancy);
        test("Dispute lifecycle and validation", AllTests::disputeLifecycle);
        test("Generic Ledger rejects bills of another utility", AllTests::ledgerTypeSafety);
        test("CSV escape/parse round trip", AllTests::csvRoundTrip);
        test("JSON parse and stringify", AllTests::jsonRoundTrip);
        test("Integration: demo data, settle-up, finalise, lock, file reload", AllTests::integration);

        System.out.println();
        System.out.println("Passed: " + passed + "   Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void test(String name, TestCase t) {
        try {
            t.run();
            passed++;
            System.out.println("  PASS  " + name);
        } catch (Throwable e) {
            failed++;
            System.out.println("  FAIL  " + name + "  ->  " + e);
        }
    }

    // ---------------- assertion helpers ----------------

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void near(double expected, double actual, String what) {
        if (Math.abs(expected - actual) > 0.005) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    static <E extends Throwable> void expectThrows(Class<E> type, TestCase t) {
        try {
            t.run();
        } catch (Throwable e) {
            if (type.isInstance(e)) {
                return;
            }
            throw new AssertionError("Expected " + type.getSimpleName() + " but got " + e);
        }
        throw new AssertionError("Expected " + type.getSimpleName() + " but nothing was thrown");
    }

    static double sum(List<Share> shares) {
        return shares.stream().mapToDouble(Share::getAmount).sum();
    }

    static List<UsageRecord> records(double[] units, int[] days) throws ValidationException {
        List<UsageRecord> list = new java.util.ArrayList<>();
        for (int i = 0; i < units.length; i++) {
            list.add(new UsageRecord("R" + (i + 1), units[i], days[i]));
        }
        return list;
    }

    static Bill bill(Utility u, double total, double fixed, String strategy, double[] units, int[] days)
            throws ValidationException {
        Bill b = new Bill("B1", u, YearMonth.of(2026, 10), total, fixed, "R1", strategy, null, "");
        for (int i = 0; i < units.length; i++) {
            b.addUsage("R" + (i + 1), units[i], days[i]);
        }
        return b;
    }

    // ---------------- unit tests ----------------

    static void equalSplit() throws Exception {
        List<Share> s = new EqualSplit().split(900, records(new double[]{0, 0, 0}, new int[]{30, 30, 30}), "kWh");
        check(s.size() == 3, "3 shares");
        near(300, s.get(0).getAmount(), "each share");
    }

    static void usageSplit() throws Exception {
        List<Share> s = new UsageBasedSplit().split(1000, records(new double[]{300, 100, 100}, new int[]{30, 30, 30}), "kWh");
        near(600, s.get(0).getAmount(), "heavy user");
        near(200, s.get(1).getAmount(), "light user");
    }

    static void proratedSplit() throws Exception {
        List<Share> s = new ProratedByDaysSplit().split(900, records(new double[]{0, 0}, new int[]{30, 15}), "");
        near(600, s.get(0).getAmount(), "full month");
        near(300, s.get(1).getAmount(), "half month");
    }

    static void roundingAddsUp() throws Exception {
        Bill b = bill(new Water(), 1000, 0, "EQUAL", new double[]{0, 0, 0}, new int[]{30, 30, 30});
        List<Share> s = b.getUtility().calculateShares(b, new EqualSplit());
        near(1000, sum(s), "total of 333.33 x 3 after rounding fix");
    }

    static void electricityTwoPart() throws Exception {
        Bill b = bill(new Electricity(), 1300, 300, "USAGE", new double[]{75, 25}, new int[]{30, 30});
        List<Share> s = b.getUtility().calculateShares(b, new UsageBasedSplit());
        // fixed 300 -> 150 each; energy 1000 -> 750 / 250
        near(900, s.get(0).getAmount(), "R1");
        near(400, s.get(1).getAmount(), "R2");
        check(s.get(0).getExplanation().startsWith("Fixed:"), "explanation shows fixed part");
    }

    static void internetRejectsUsage() throws Exception {
        Bill b = bill(new Internet(), 999, 0, "USAGE", new double[]{10, 20}, new int[]{30, 30});
        expectThrows(InvalidBillException.class, () -> b.getUtility().calculateShares(b, new UsageBasedSplit()));
    }

    static void groceries() throws Exception {
        Groceries g = new Groceries();
        check(g.getDefaultStrategy() instanceof ProratedByDaysSplit, "default rule is prorated");
        Bill b = bill(g, 6000, 0, "PRORATED", new double[]{0, 0}, new int[]{30, 10});
        List<Share> s = g.calculateShares(b, g.getDefaultStrategy());
        near(4500, s.get(0).getAmount(), "30 of 40 days");
        near(1500, s.get(1).getAmount(), "10 of 40 days");
        expectThrows(InvalidBillException.class, () -> g.calculateShares(b, new UsageBasedSplit()));
    }

    static void miscellaneous() throws Exception {
        Miscellaneous m = new Miscellaneous();
        Bill noNote = bill(m, 1200, 0, "EQUAL", new double[]{0, 0, 0}, new int[]{31, 31, 31});
        expectThrows(InvalidBillException.class, () -> m.calculateShares(noNote, new EqualSplit()));
        Bill withNote = new Bill("B2", m, YearMonth.of(2026, 10), 1200, 0, "R1", "EQUAL", null, "Plumber visit");
        withNote.addUsage("R1", 0, 31);
        withNote.addUsage("R2", 0, 31);
        withNote.addUsage("R3", 0, 31);
        near(400, m.calculateShares(withNote, new EqualSplit()).get(1).getAmount(), "equal third");
    }

    static void negativeUsage() {
        expectThrows(NegativeUsageException.class, () -> new UsageRecord("R1", -5, 30));
        expectThrows(NegativeUsageException.class, () -> new UsageRecord("R1", 5, -1));
    }

    static void zeroUnits() {
        expectThrows(InvalidBillException.class, () ->
                new UsageBasedSplit().split(500, records(new double[]{0, 0}, new int[]{30, 30}), "kWh"));
    }

    static void fixedTooBig() {
        expectThrows(ValidationException.class, () -> bill(new Electricity(), 100, 200, "USAGE",
                new double[]{1}, new int[]{30}));
    }

    static void daysPresent() throws Exception {
        Roommate r = new Roommate("R1", "Meera", "", "", LocalDate.of(2026, 9, 12), LocalDate.of(2026, 10, 20));
        check(r.daysPresentIn(YearMonth.of(2026, 8)) == 0, "before move-in");
        check(r.daysPresentIn(YearMonth.of(2026, 9)) == 19, "Sep 12-30 = 19 days");
        check(r.daysPresentIn(YearMonth.of(2026, 10)) == 20, "Oct 1-20 = 20 days");
        check(r.daysPresentIn(YearMonth.of(2026, 11)) == 0, "after move-out");
    }

    static void badOccupancy() {
        expectThrows(ValidationException.class, () ->
                new Roommate("R1", "X", "", "", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1)));
        expectThrows(ValidationException.class, () -> new Roommate("R1", " ", LocalDate.now()));
    }

    static void disputeLifecycle() throws Exception {
        Dispute d = new Dispute("D1", "B1", "R1", "Reading looks wrong");
        check(d.getStatus().isUnresolved(), "new dispute is open");
        expectThrows(ValidationException.class, () -> d.resolve(""));
        d.resolve("Corrected");
        check(!d.getStatus().isUnresolved(), "resolved");
        expectThrows(ValidationException.class, () -> d.reject("again"));
        expectThrows(ValidationException.class, () -> new Dispute("D2", "B1", "R1", "bad"));
    }

    static void ledgerTypeSafety() throws Exception {
        Ledger<Electricity> ledger = new Ledger<>(new Electricity());
        ledger.record(bill(new Electricity(), 500, 0, "EQUAL", new double[]{0}, new int[]{30}));
        near(500, ledger.totalFor(YearMonth.of(2026, 10)), "ledger total");
        expectThrows(IllegalArgumentException.class, () ->
                ledger.record(bill(new Water(), 100, 0, "EQUAL", new double[]{0}, new int[]{30})));
    }

    static void csvRoundTrip() {
        String line = CsvUtil.toLine("a", "b,c", "say \"hi\"", "multi\nline");
        List<String[]> rows = CsvUtil.parse("h1,h2,h3,h4\n" + line + "\n");
        check(rows.size() == 2, "2 rows");
        check(rows.get(1)[1].equals("b,c"), "comma kept");
        check(rows.get(1)[2].equals("say \"hi\""), "quotes kept");
        check(rows.get(1)[3].equals("multi\nline"), "newline kept");
    }

    @SuppressWarnings("unchecked")
    static void jsonRoundTrip() {
        Map<String, Object> m = Json.parseObject("{\"a\":1.5,\"b\":[true,null,\"x\\\"y\"],\"c\":{}}");
        near(1.5, (Double) m.get("a"), "number");
        check(((List<Object>) m.get("b")).get(2).equals("x\"y"), "escaped string");
        check(Json.stringify(m).equals("{\"a\":1.5,\"b\":[true,null,\"x\\\"y\"],\"c\":{}}"), "stringify");
    }

    // ---------------- integration test ----------------

    static void integration() throws Exception {
        Path dir = Files.createTempDirectory("resolver-test");
        try {
            AppContext ctx = new AppContext(dir.resolve("data"), dir.resolve("reports"));
            ctx.demo().load();
            YearMonth oct = YearMonth.of(2026, 10);
            YearMonth sep = YearMonth.of(2026, 9);

            MonthSummary s = ctx.settlement().summarize(oct);
            check(s.bills.size() == 6, "6 bills in October (4 utilities + groceries + misc)");
            double shares = s.balances.stream().mapToDouble(b -> b.getTotalShare()).sum();
            near(s.grandTotal, shares, "shares add to grand total");
            double net = s.balances.stream().mapToDouble(b -> b.getNet()).sum();
            near(0, net, "nets cancel out");
            check(ctx.settlement().summarize(sep).isSettled(), "September fully settled by demo payments");

            // finalising October must fail because of the open dispute
            expectThrows(UnresolvedDisputeException.class, () -> ctx.periods().finalizeMonth(oct));

            // record the suggested transfers -> October becomes settled
            for (Transfer t : s.transfers) {
                ctx.payments().record(t.getFromId(), t.getToId(), t.getAmount(), LocalDate.now(), oct, "test");
            }
            check(ctx.settlement().summarize(oct).isSettled(), "October settled after transfers");

            // resolve dispute -> finalise works -> month is locked
            for (Dispute d : ctx.disputes().unresolvedFor(oct)) {
                ctx.disputes().changeStatus(d.getId(), "REJECTED", "Reading verified");
            }
            ctx.periods().finalizeMonth(oct);
            BillRequest req = new BillRequest();
            req.utilityType = "WATER";
            req.month = oct;
            req.totalAmount = 100;
            req.paidById = "R1";
            req.usage.add(new BillRequest.UsageEntry("R1", 0, 31));
            expectThrows(MonthLockedException.class, () -> ctx.billing().create(req));

            // report files are written
            Path[] files = ctx.reports().export(oct);
            check(Files.size(files[0]) > 500 && Files.size(files[1]) > 50, "report files written");

            // reload everything from the CSV files and compare
            AppContext reloaded = new AppContext(dir.resolve("data"), dir.resolve("reports"));
            check(reloaded.billing().all().size() == ctx.billing().all().size(), "bills reloaded from CSV");
            check(reloaded.periods().isFinalized(oct), "finalised month reloaded");
            near(s.grandTotal, reloaded.settlement().summarize(oct).grandTotal, "same totals after reload");
        } finally {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }
}
