package com.utilityresolver.util;

import com.utilityresolver.model.Share;

import java.util.List;

/** Money helpers: rounding to paise and formatting with the rupee symbol. */
public final class Money {
    public static final String SYMBOL = "₹";

    private Money() {
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public static String format(double v) {
        return SYMBOL + String.format("%,.2f", v);
    }

    /** Plain number for CSV files (no symbol, no grouping). */
    public static String plain(double v) {
        return String.format("%.2f", v);
    }

    /**
     * Rounds every share to 2 decimals and gives any leftover paisa to the largest
     * share, so the shares always add up exactly to the bill total.
     */
    public static List<Share> balanceRounding(List<Share> shares, double total) {
        if (shares.isEmpty()) {
            return shares;
        }
        double sum = 0;
        Share largest = shares.get(0);
        for (Share s : shares) {
            s.setAmount(round2(s.getAmount()));
            sum += s.getAmount();
            if (s.getAmount() > largest.getAmount()) {
                largest = s;
            }
        }
        double diff = round2(round2(total) - sum);
        if (diff != 0) {
            largest.setAmount(round2(largest.getAmount() + diff));
            largest.appendExplanation(" (" + (diff > 0 ? "+" : "") + plain(diff) + " rounding)");
        }
        return shares;
    }
}
