package com.utilityresolver.service;

import com.utilityresolver.exception.AppException;
import com.utilityresolver.model.Payment;
import com.utilityresolver.storage.DataStore;
import com.utilityresolver.util.Money;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Records money transferred between roommates. */
public class PaymentService {
    private final DataStore store;
    private final RoommateService roommates;
    private final PeriodService periods;

    public PaymentService(DataStore store, RoommateService roommates, PeriodService periods) {
        this.store = store;
        this.roommates = roommates;
        this.periods = periods;
    }

    public List<Payment> forMonth(YearMonth month) {
        return store.payments().findWhere(p -> p.getMonth().equals(month)).stream()
                .sorted(Comparator.comparing(Payment::getDate).thenComparing(Payment::getId))
                .collect(Collectors.toList());
    }

    public Payment record(String fromId, String toId, double amount, LocalDate date, YearMonth month, String note)
            throws AppException, IOException {
        roommates.get(fromId);
        roommates.get(toId);
        periods.ensureEditable(month);
        Payment p = new Payment(store.paymentIds().next(), fromId, toId, amount, date, month, note);
        store.payments().save(p);
        store.audit().log("PAYMENT_RECORDED", p.getId() + " " + roommates.nameOf(fromId) + " -> "
                + roommates.nameOf(toId) + " " + Money.format(amount) + " for " + month);
        return p;
    }

    public void delete(String id) throws AppException, IOException {
        Payment p = store.payments().getOrThrow(id);
        periods.ensureEditable(p.getMonth());
        store.payments().delete(id);
        store.audit().log("PAYMENT_DELETED", id + " " + Money.format(p.getAmount()));
    }
}
