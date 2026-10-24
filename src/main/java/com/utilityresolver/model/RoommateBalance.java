package com.utilityresolver.model;

/** Per-roommate totals for one month, used by the settlement report. */
public class RoommateBalance {
    private final Roommate roommate;
    private double totalShare;      // what they should bear
    private double paidToProvider;  // bills they paid to the company
    private double paymentsSent;    // money sent to other roommates
    private double paymentsReceived;

    public RoommateBalance(Roommate roommate) {
        this.roommate = roommate;
    }

    public void addShare(double v) { totalShare += v; }

    public void addPaidToProvider(double v) { paidToProvider += v; }

    public void addPaymentSent(double v) { paymentsSent += v; }

    public void addPaymentReceived(double v) { paymentsReceived += v; }

    public Roommate getRoommate() { return roommate; }

    public double getTotalShare() { return totalShare; }

    public double getPaidToProvider() { return paidToProvider; }

    public double getPaymentsSent() { return paymentsSent; }

    public double getPaymentsReceived() { return paymentsReceived; }

    /** Positive = others owe this roommate; negative = this roommate owes money. */
    public double getNet() {
        return paidToProvider + paymentsSent - paymentsReceived - totalShare;
    }
}
