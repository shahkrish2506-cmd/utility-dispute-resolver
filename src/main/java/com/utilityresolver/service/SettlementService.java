package com.utilityresolver.service;

import com.utilityresolver.exception.AppException;
import com.utilityresolver.model.Bill;
import com.utilityresolver.model.Payment;
import com.utilityresolver.model.Roommate;
import com.utilityresolver.model.RoommateBalance;
import com.utilityresolver.model.Share;
import com.utilityresolver.model.Transfer;
import com.utilityresolver.util.Money;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out who owes whom for a month:
 * net = (paid to provider) + (payments sent) - (payments received) - (fair share).
 * Then a greedy algorithm suggests the fewest transfers to settle everyone.
 */
public class SettlementService {
    private static final double EPS = 0.005;
    private final RoommateService roommates;
    private final BillingService billing;
    private final PaymentService payments;
    private final DisputeService disputes;
    private final PeriodService periods;

    public SettlementService(RoommateService roommates, BillingService billing, PaymentService payments,
                             DisputeService disputes, PeriodService periods) {
        this.roommates = roommates;
        this.billing = billing;
        this.payments = payments;
        this.disputes = disputes;
        this.periods = periods;
    }

    public MonthSummary summarize(YearMonth month) {
        MonthSummary s = new MonthSummary(month, periods.isFinalized(month));
        Map<String, RoommateBalance> balances = new LinkedHashMap<>();
        for (Roommate r : roommates.all()) {
            balances.put(r.getId(), new RoommateBalance(r));
        }

        for (Bill bill : billing.forMonth(month)) {
            s.bills.add(bill);
            s.grandTotal += bill.getTotalAmount();
            s.totalsByUtility.merge(bill.getUtility().getType(), bill.getTotalAmount(), Double::sum);
            RoommateBalance payer = balances.get(bill.getPaidById());
            if (payer != null) {
                payer.addPaidToProvider(bill.getTotalAmount());
            }
            try {
                List<Share> shares = billing.calculateShares(bill);
                s.sharesByBill.put(bill.getId(), shares);
                for (Share sh : shares) {
                    RoommateBalance b = balances.get(sh.getRoommateId());
                    if (b != null) {
                        b.addShare(sh.getAmount());
                    }
                }
            } catch (AppException e) {
                s.billErrors.put(bill.getId(), e.getMessage());
            }
        }

        for (Payment p : payments.forMonth(month)) {
            s.payments.add(p);
            RoommateBalance from = balances.get(p.getFromId());
            RoommateBalance to = balances.get(p.getToId());
            if (from != null) {
                from.addPaymentSent(p.getAmount());
            }
            if (to != null) {
                to.addPaymentReceived(p.getAmount());
            }
        }

        for (RoommateBalance b : balances.values()) {
            boolean relevant = b.getRoommate().daysPresentIn(month) > 0 || Math.abs(b.getTotalShare()) > EPS
                    || Math.abs(b.getNet()) > EPS || b.getPaidToProvider() > 0;
            if (relevant) {
                s.balances.add(b);
            }
        }
        s.disputes.addAll(disputes.forMonth(month));
        s.transfers.addAll(suggestTransfers(s.balances));
        return s;
    }

    /** Greedy: largest debtor pays largest creditor until everyone is at zero. */
    public static List<Transfer> suggestTransfers(List<RoommateBalance> balances) {
        List<double[]> creditors = new ArrayList<>();
        List<double[]> debtors = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < balances.size(); i++) {
            RoommateBalance b = balances.get(i);
            ids.add(b.getRoommate().getId());
            double net = Money.round2(b.getNet());
            if (net > EPS) {
                creditors.add(new double[]{i, net});
            } else if (net < -EPS) {
                debtors.add(new double[]{i, -net});
            }
        }
        Comparator<double[]> byAmountDesc = (a, b) -> Double.compare(b[1], a[1]);
        List<Transfer> result = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            creditors.sort(byAmountDesc);
            debtors.sort(byAmountDesc);
            double[] c = creditors.get(0);
            double[] d = debtors.get(0);
            double amt = Money.round2(Math.min(c[1], d[1]));
            if (amt > EPS) {
                result.add(new Transfer(ids.get((int) d[0]), ids.get((int) c[0]), amt));
            }
            c[1] = Money.round2(c[1] - amt);
            d[1] = Money.round2(d[1] - amt);
            if (c[1] <= EPS) {
                creditors.remove(0);
            }
            if (d[1] <= EPS) {
                debtors.remove(0);
            }
        }
        return result;
    }
}
