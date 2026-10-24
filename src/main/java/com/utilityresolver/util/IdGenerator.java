package com.utilityresolver.util;

import java.util.Collection;

/** Generates readable ids like R1, B12, P3, D7 that keep increasing. */
public class IdGenerator {
    private final String prefix;
    private int counter;

    public IdGenerator(String prefix) {
        this.prefix = prefix;
    }

    /** Continue numbering after the highest existing id (used after loading files). */
    public void syncWith(Collection<String> existingIds) {
        for (String id : existingIds) {
            if (id.startsWith(prefix)) {
                try {
                    counter = Math.max(counter, Integer.parseInt(id.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // id not in our format, skip it
                }
            }
        }
    }

    public synchronized String next() {
        counter++;
        return prefix + counter;
    }

    public void reset() {
        counter = 0;
    }
}
