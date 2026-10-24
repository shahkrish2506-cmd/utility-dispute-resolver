package com.utilityresolver.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.utilityresolver.exception.AppException;
import com.utilityresolver.exception.EntityNotFoundException;
import com.utilityresolver.exception.MonthLockedException;
import com.utilityresolver.exception.UnresolvedDisputeException;
import com.utilityresolver.model.Bill;
import com.utilityresolver.model.Dispute;
import com.utilityresolver.model.Payment;
import com.utilityresolver.model.Roommate;
import com.utilityresolver.model.utility.Utility;
import com.utilityresolver.model.utility.UtilityType;
import com.utilityresolver.service.AppContext;
import com.utilityresolver.service.BillRequest;
import com.utilityresolver.strategy.SplitStrategy;
import com.utilityresolver.strategy.StrategyFactory;
import com.utilityresolver.util.Json;
import com.utilityresolver.util.Ledger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * REST-style JSON API used by the web front end. Routes on "/api/...".
 * Every AppException is turned into a JSON error message with a suitable HTTP status.
 */
public class ApiHandler implements HttpHandler {
    private final AppContext ctx;
    private final JsonMapper map;

    public ApiHandler(AppContext ctx) {
        this.ctx = ctx;
        this.map = new JsonMapper(ctx);
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        Request req = new Request(ex);
        try {
            Object result;
            synchronized (ctx) { // one request at a time keeps the CSV files consistent
                result = route(req);
            }
            if (result instanceof RawResponse) {
                RawResponse raw = (RawResponse) result;
                send(ex, 200, raw.contentType, raw.body, raw.downloadName);
            } else {
                send(ex, 200, "application/json; charset=utf-8", Json.stringify(result), null);
            }
        } catch (UnresolvedDisputeException e) {
            Map<String, Object> err = error(e.getMessage());
            err.put("disputeIds", e.getDisputeIds());
            send(ex, 409, "application/json; charset=utf-8", Json.stringify(err), null);
        } catch (MonthLockedException e) {
            sendError(ex, 423, e.getMessage());
        } catch (EntityNotFoundException e) {
            sendError(ex, 404, e.getMessage());
        } catch (AppException e) {
            sendError(ex, 400, e.getMessage());
        } catch (HttpError e) {
            sendError(ex, e.getStatus(), e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "Unexpected error: " + e);
        }
    }

    private Object route(Request r) throws Exception {
        String res = r.part(0);
        if (res == null) {
            throw new HttpError(404, "Unknown API path");
        }
        String m = r.method();
        switch (res) {
            case "meta": return meta();
            case "apartment": return apartment(r, m);
            case "roommates": return roommates(r, m);
            case "months": return months();
            case "prefill": return prefill(r);
            case "preview": return map.shares(ctx.billing().preview(billRequest(r)));
            case "bills": return bills(r, m);
            case "payments": return payments(r, m);
            case "disputes": return disputes(r, m);
            case "summary": return map.summary(ctx.settlement().summarize(r.monthParam()));
            case "trends": return trends();
            case "period": return period(r, m);
            case "report": return report(r, m);
            case "audit": return ctx.store().audit().recent(200);
            case "demo":
                requirePost(m);
                ctx.demo().load();
                return ok("Demo data loaded");
            case "reset":
                requirePost(m);
                ctx.store().wipe();
                return ok("All data cleared");
            default:
                throw new HttpError(404, "Unknown API path: " + res);
        }
    }

    // ------------------------------------------------------------------ meta / apartment

    private Object meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Object> utilities = new ArrayList<>();
        for (UtilityType t : UtilityType.values()) {
            utilities.add(map.utility(t.create()));
        }
        List<Object> strategies = new ArrayList<>();
        for (SplitStrategy s : StrategyFactory.all()) {
            strategies.add(map.strategy(s));
        }
        m.put("utilities", utilities);
        m.put("strategies", strategies);
        m.put("today", LocalDate.now().toString());
        m.put("currentMonth", YearMonth.now().toString());
        m.put("dataDir", ctx.store().getDataDir().toAbsolutePath().toString());
        m.put("reportsDir", ctx.store().getReportsDir().toAbsolutePath().toString());
        m.put("hasData", !ctx.store().roommates().findAll().isEmpty());
        m.put("apartment", apartmentJson());
        return m;
    }

    private Map<String, Object> apartmentJson() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("name", ctx.store().getApartment().getName());
        a.put("address", ctx.store().getApartment().getAddress());
        a.put("roommateCount", ctx.store().getApartment().getRoommates().size());
        return a;
    }

    private Object apartment(Request r, String m) throws Exception {
        if (m.equals("PUT") || m.equals("POST")) {
            ctx.store().getApartment().update(r.str("name"), r.str("address"));
            ctx.store().saveApartment();
            ctx.store().audit().log("APARTMENT_UPDATED", ctx.store().getApartment().getName());
        }
        return apartmentJson();
    }

    // ------------------------------------------------------------------ roommates

    private Object roommates(Request r, String m) throws Exception {
        String id = r.part(1);
        YearMonth month = r.query("month") == null ? null : r.monthParam();
        if (m.equals("GET")) {
            if (id != null) {
                return map.roommate(ctx.roommates().get(id), month);
            }
            List<Object> list = new ArrayList<>();
            for (Roommate rm : ctx.roommates().all()) {
                list.add(map.roommate(rm, month));
            }
            return list;
        }
        if (m.equals("POST") && id == null) {
            Roommate rm = ctx.roommates().add(r.str("name"), r.str("email"), r.str("phone"),
                    r.date("joinDate"), r.date("leaveDate"));
            return map.roommate(rm, month);
        }
        if (m.equals("PUT") && id != null) {
            Roommate rm = ctx.roommates().update(id, r.str("name"), r.str("email"), r.str("phone"),
                    r.date("joinDate"), r.date("leaveDate"));
            return map.roommate(rm, month);
        }
        if (m.equals("DELETE") && id != null) {
            ctx.roommates().remove(id);
            return ok("Roommate removed");
        }
        throw new HttpError(405, "Method not allowed");
    }

    // ------------------------------------------------------------------ bills

    private Object months() {
        Map<String, Object> m = new LinkedHashMap<>();
        SortedSet<String> all = new TreeSet<>();
        ctx.billing().monthsWithData().forEach(x -> all.add(x.toString()));
        all.add(YearMonth.now().toString());
        List<String> list = new ArrayList<>(all);
        java.util.Collections.reverse(list);
        m.put("months", list);
        List<String> fin = new ArrayList<>();
        ctx.store().getFinalizedMonths().forEach(x -> fin.add(x.toString()));
        m.put("finalized", fin);
        return m;
    }

    private Object prefill(Request r) {
        YearMonth month = r.monthParam();
        List<Object> list = new ArrayList<>();
        for (Roommate rm : ctx.roommates().all()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("roommateId", rm.getId());
            e.put("name", rm.getName());
            e.put("days", rm.daysPresentIn(month));
            e.put("monthDays", month.lengthOfMonth());
            list.add(e);
        }
        return list;
    }

    private BillRequest billRequest(Request r) throws IOException {
        BillRequest b = new BillRequest();
        b.utilityType = r.str("utility");
        b.month = r.month("month");
        b.totalAmount = r.num("totalAmount");
        b.fixedCharge = r.num("fixedCharge");
        b.paidById = r.str("paidById");
        b.strategyKey = r.str("strategy");
        b.dueDate = r.date("dueDate");
        b.notes = r.str("notes");
        Object usage = r.body().get("usage");
        if (usage instanceof List) {
            for (Object o : (List<?>) usage) {
                if (o instanceof Map) {
                    Map<?, ?> u = (Map<?, ?>) o;
                    Object include = u.get("include");
                    if (Boolean.FALSE.equals(include)) {
                        continue;
                    }
                    String rid = String.valueOf(u.get("roommateId"));
                    double units = toDouble(u.get("units"));
                    double days = toDouble(u.get("days"));
                    if (days != Math.rint(days)) {
                        throw new HttpError(400, "Days present must be a whole number");
                    }
                    b.usage.add(new BillRequest.UsageEntry(rid, units, (int) days));
                }
            }
        }
        return b;
    }

    private static double toDouble(Object o) {
        if (o == null || o.toString().isBlank()) {
            return 0;
        }
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        try {
            return Double.parseDouble(o.toString().trim());
        } catch (NumberFormatException e) {
            throw new HttpError(400, "'" + o + "' is not a number");
        }
    }

    private Object bills(Request r, String m) throws Exception {
        String id = r.part(1);
        if (m.equals("GET")) {
            if (id != null) {
                Bill b = ctx.billing().get(id);
                return map.bill(b, ctx.billing().calculateShares(b), null);
            }
            List<Object> list = new ArrayList<>();
            for (Bill b : ctx.billing().forMonth(r.monthParam())) {
                list.add(map.bill(b, null, null));
            }
            return list;
        }
        if (m.equals("POST") && id == null) {
            Bill b = ctx.billing().create(billRequest(r));
            return map.bill(b, ctx.billing().calculateShares(b), null);
        }
        if (m.equals("PUT") && id != null) {
            Bill b = ctx.billing().update(id, billRequest(r));
            return map.bill(b, ctx.billing().calculateShares(b), null);
        }
        if (m.equals("DELETE") && id != null) {
            ctx.billing().delete(id);
            return ok("Bill deleted");
        }
        throw new HttpError(405, "Method not allowed");
    }

    // ------------------------------------------------------------------ payments / disputes

    private Object payments(Request r, String m) throws Exception {
        String id = r.part(1);
        if (m.equals("GET")) {
            List<Object> list = new ArrayList<>();
            for (Payment p : ctx.payments().forMonth(r.monthParam())) {
                list.add(map.payment(p));
            }
            return list;
        }
        if (m.equals("POST") && id == null) {
            LocalDate date = r.date("date");
            Payment p = ctx.payments().record(r.str("fromId"), r.str("toId"), r.num("amount"),
                    date == null ? LocalDate.now() : date, r.month("month"), r.str("note"));
            return map.payment(p);
        }
        if (m.equals("DELETE") && id != null) {
            ctx.payments().delete(id);
            return ok("Payment deleted");
        }
        throw new HttpError(405, "Method not allowed");
    }

    private Object disputes(Request r, String m) throws Exception {
        String id = r.part(1);
        if (m.equals("GET")) {
            String status = r.query("status");
            List<Dispute> source = r.query("month") != null
                    ? ctx.disputes().forMonth(r.monthParam()) : ctx.disputes().all();
            List<Object> list = new ArrayList<>();
            for (Dispute d : source) {
                boolean keep = status == null || status.isBlank() || status.equals("ALL")
                        || (status.equals("UNRESOLVED") && d.getStatus().isUnresolved())
                        || d.getStatus().name().equals(status);
                if (keep) {
                    list.add(map.dispute(d));
                }
            }
            return list;
        }
        if (m.equals("POST") && id == null) {
            return map.dispute(ctx.disputes().raise(r.str("billId"), r.str("raisedById"), r.str("reason")));
        }
        if (m.equals("POST") && id != null && "status".equals(r.part(2))) {
            return map.dispute(ctx.disputes().changeStatus(id, r.str("status"), r.str("note")));
        }
        throw new HttpError(405, "Method not allowed");
    }

    // ------------------------------------------------------------------ trends / periods / reports

    private Object trends() {
        Map<UtilityType, Ledger<Utility>> ledgers = ctx.billing().allLedgers();
        SortedSet<YearMonth> months = new TreeSet<>();
        ledgers.values().forEach(l -> months.addAll(l.monthlyTotals().keySet()));
        List<String> labels = new ArrayList<>();
        months.forEach(x -> labels.add(x.toString()));
        Map<String, Object> series = new LinkedHashMap<>();
        Map<String, Object> stats = new LinkedHashMap<>();
        for (Map.Entry<UtilityType, Ledger<Utility>> e : ledgers.entrySet()) {
            List<Double> values = new ArrayList<>();
            for (YearMonth ym : months) {
                values.add(e.getValue().totalFor(ym));
            }
            series.put(e.getKey().name(), values);
            Map<String, Object> st = new LinkedHashMap<>();
            st.put("average", e.getValue().averageMonthly());
            st.put("bills", e.getValue().getEntries().size());
            e.getValue().highestBill().ifPresent(b -> {
                st.put("highest", b.getTotalAmount());
                st.put("highestMonth", b.getMonth().toString());
            });
            if (!months.isEmpty()) {
                st.put("changeVsPrevious", e.getValue().changeVsPrevious(months.last()));
            }
            stats.put(e.getKey().name(), st);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("months", labels);
        out.put("series", series);
        out.put("stats", stats);
        return out;
    }

    private Object period(Request r, String m) throws Exception {
        requirePost(m);
        YearMonth month = Request.parseMonth(r.part(1) == null ? "" : r.part(1));
        String action = r.part(2);
        if ("finalize".equals(action)) {
            ctx.periods().finalizeMonth(month);
            ctx.reports().export(month);
            return ok("Month " + month + " finalised and report saved");
        }
        if ("reopen".equals(action)) {
            ctx.periods().reopenMonth(month);
            return ok("Month " + month + " re-opened");
        }
        throw new HttpError(404, "Unknown period action");
    }

    private Object report(Request r, String m) throws Exception {
        YearMonth month = r.monthParam();
        if (m.equals("POST")) {
            Path[] files = ctx.reports().export(month);
            Map<String, Object> out = ok("Report saved");
            out.put("txt", files[0].toAbsolutePath().toString());
            out.put("csv", files[1].toAbsolutePath().toString());
            return out;
        }
        String format = r.query("format") == null ? "txt" : r.query("format");
        boolean download = "1".equals(r.query("download"));
        if (format.equals("csv")) {
            return new RawResponse("text/csv; charset=utf-8", "﻿" + ctx.reports().buildCsv(month),
                    download ? "settlement_" + month + ".csv" : null);
        }
        return new RawResponse("text/plain; charset=utf-8", ctx.reports().buildText(month),
                download ? "settlement_" + month + ".txt" : null);
    }

    // ------------------------------------------------------------------ helpers

    private static void requirePost(String m) {
        if (!m.equals("POST")) {
            throw new HttpError(405, "Use POST");
        }
    }

    private static Map<String, Object> ok(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("message", message);
        return m;
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", message);
        return m;
    }

    private static void sendError(HttpExchange ex, int status, String message) throws IOException {
        send(ex, status, "application/json; charset=utf-8", Json.stringify(error(message)), null);
    }

    private static void send(HttpExchange ex, int status, String type, String body, String downloadName)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        if (downloadName != null) {
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + downloadName + "\"");
        }
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Non-JSON response (report text / CSV). */
    private static final class RawResponse {
        final String contentType;
        final String body;
        final String downloadName;

        RawResponse(String contentType, String body, String downloadName) {
            this.contentType = contentType;
            this.body = body;
            this.downloadName = downloadName;
        }
    }
}
