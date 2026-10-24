package com.utilityresolver.web;

import com.utilityresolver.model.Bill;
import com.utilityresolver.model.Dispute;
import com.utilityresolver.model.Payment;
import com.utilityresolver.model.Roommate;
import com.utilityresolver.model.RoommateBalance;
import com.utilityresolver.model.Share;
import com.utilityresolver.model.Transfer;
import com.utilityresolver.model.UsageRecord;
import com.utilityresolver.service.AppContext;
import com.utilityresolver.service.MonthSummary;
import com.utilityresolver.strategy.SplitStrategy;
import com.utilityresolver.model.utility.Utility;
import com.utilityresolver.util.Money;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts model objects into Maps/Lists that Json.stringify() can write. */
public class JsonMapper {
    private final AppContext ctx;

    public JsonMapper(AppContext ctx) {
        this.ctx = ctx;
    }

    private String name(String roommateId) {
        return ctx.roommates().nameOf(roommateId);
    }

    public Map<String, Object> roommate(Roommate r, YearMonth month) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("name", r.getName());
        m.put("email", r.getEmail());
        m.put("phone", r.getPhone());
        m.put("role", r.getRole());
        m.put("joinDate", str(r.getJoinDate()));
        m.put("leaveDate", str(r.getLeaveDate()));
        m.put("active", r.isActiveOn(LocalDate.now()));
        if (month != null) {
            m.put("daysThisMonth", r.daysPresentIn(month));
        }
        return m;
    }

    public Map<String, Object> utility(Utility u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", u.getType().name());
        m.put("name", u.getDisplayName());
        m.put("unit", u.getUnitLabel());
        m.put("metered", u.isMetered());
        m.put("defaultStrategy", u.getDefaultStrategy().getKey());
        m.put("rule", u.getBillingRule());
        return m;
    }

    public Map<String, Object> strategy(SplitStrategy s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", s.getKey());
        m.put("name", s.getDisplayName());
        m.put("description", s.getDescription());
        m.put("requiresUsage", s.requiresUsage());
        return m;
    }

    public List<Object> shares(List<Share> shares) {
        List<Object> list = new ArrayList<>();
        for (Share s : shares) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("roommateId", s.getRoommateId());
            m.put("name", name(s.getRoommateId()));
            m.put("amount", s.getAmount());
            m.put("explanation", s.getExplanation());
            list.add(m);
        }
        return list;
    }

    public Map<String, Object> bill(Bill b, List<Share> shares, String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("utility", b.getUtility().getType().name());
        m.put("utilityName", b.getUtility().getDisplayName());
        m.put("unit", b.getUtility().getUnitLabel());
        m.put("month", b.getMonth().toString());
        m.put("totalAmount", b.getTotalAmount());
        m.put("fixedCharge", b.getFixedCharge());
        m.put("paidById", b.getPaidById());
        m.put("paidByName", name(b.getPaidById()));
        m.put("strategy", b.getStrategyKey());
        m.put("dueDate", str(b.getDueDate()));
        m.put("notes", b.getNotes());
        m.put("totalUnits", b.getTotalUnits());
        List<Object> usage = new ArrayList<>();
        for (UsageRecord u : b.getUsageRecords()) {
            Map<String, Object> um = new LinkedHashMap<>();
            um.put("roommateId", u.getRoommateId());
            um.put("name", name(u.getRoommateId()));
            um.put("units", u.getUnits());
            um.put("days", u.getDaysPresent());
            usage.add(um);
        }
        m.put("usage", usage);
        long openDisputes = ctx.store().disputes()
                .findWhere(d -> d.getBillId().equals(b.getId()) && d.getStatus().isUnresolved()).size();
        m.put("openDisputes", openDisputes);
        if (shares != null) {
            m.put("shares", shares(shares));
        }
        if (error != null) {
            m.put("error", error);
        }
        return m;
    }

    public Map<String, Object> payment(Payment p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("fromId", p.getFromId());
        m.put("fromName", name(p.getFromId()));
        m.put("toId", p.getToId());
        m.put("toName", name(p.getToId()));
        m.put("amount", p.getAmount());
        m.put("date", str(p.getDate()));
        m.put("month", p.getMonth().toString());
        m.put("note", p.getNote());
        return m;
    }

    public Map<String, Object> dispute(Dispute d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("billId", d.getBillId());
        ctx.store().bills().findById(d.getBillId()).ifPresent(b -> {
            m.put("billLabel", b.getUtility().getDisplayName() + " " + b.getMonth() + " ("
                    + Money.format(b.getTotalAmount()) + ")");
            m.put("month", b.getMonth().toString());
        });
        m.put("raisedById", d.getRaisedById());
        m.put("raisedByName", name(d.getRaisedById()));
        m.put("reason", d.getReason());
        m.put("raisedOn", str(d.getRaisedOn()));
        m.put("status", d.getStatus().name());
        m.put("statusLabel", d.getStatus().getLabel());
        m.put("unresolved", d.getStatus().isUnresolved());
        m.put("resolutionNote", d.getResolutionNote());
        m.put("closedOn", str(d.getClosedOn()));
        return m;
    }

    public Map<String, Object> summary(MonthSummary s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("month", s.month.toString());
        m.put("finalized", s.finalized);
        m.put("grandTotal", s.grandTotal);
        Map<String, Object> totals = new LinkedHashMap<>();
        s.totalsByUtility.forEach((k, v) -> totals.put(k.name(), v));
        m.put("totalsByUtility", totals);
        List<Object> bills = new ArrayList<>();
        for (Bill b : s.bills) {
            bills.add(bill(b, s.sharesByBill.get(b.getId()), s.billErrors.get(b.getId())));
        }
        m.put("bills", bills);
        List<Object> balances = new ArrayList<>();
        for (RoommateBalance b : s.balances) {
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("roommateId", b.getRoommate().getId());
            bm.put("name", b.getRoommate().getName());
            bm.put("days", b.getRoommate().daysPresentIn(s.month));
            bm.put("share", Money.round2(b.getTotalShare()));
            bm.put("paidToProvider", Money.round2(b.getPaidToProvider()));
            bm.put("sent", Money.round2(b.getPaymentsSent()));
            bm.put("received", Money.round2(b.getPaymentsReceived()));
            bm.put("net", Money.round2(b.getNet()));
            balances.add(bm);
        }
        m.put("balances", balances);
        List<Object> transfers = new ArrayList<>();
        for (Transfer t : s.transfers) {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("fromId", t.getFromId());
            tm.put("fromName", name(t.getFromId()));
            tm.put("toId", t.getToId());
            tm.put("toName", name(t.getToId()));
            tm.put("amount", t.getAmount());
            transfers.add(tm);
        }
        m.put("transfers", transfers);
        List<Object> payments = new ArrayList<>();
        s.payments.forEach(p -> payments.add(payment(p)));
        m.put("payments", payments);
        List<Object> disputes = new ArrayList<>();
        s.disputes.forEach(d -> disputes.add(dispute(d)));
        m.put("disputes", disputes);
        m.put("unresolvedDisputes", s.unresolvedDisputes());
        m.put("settled", s.isSettled());
        return m;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
