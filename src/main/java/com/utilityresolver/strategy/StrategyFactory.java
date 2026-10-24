package com.utilityresolver.strategy;

import com.utilityresolver.exception.ValidationException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Registry of available strategies, looked up by key (stored in the bill file). */
public final class StrategyFactory {
    private static final Map<String, SplitStrategy> STRATEGIES = new LinkedHashMap<>();

    static {
        register(new EqualSplit());
        register(new UsageBasedSplit());
        register(new ProratedByDaysSplit());
    }

    private StrategyFactory() {
    }

    private static void register(SplitStrategy s) {
        STRATEGIES.put(s.getKey(), s);
    }

    public static SplitStrategy get(String key) throws ValidationException {
        SplitStrategy s = STRATEGIES.get(key == null ? "" : key.trim().toUpperCase());
        if (s == null) {
            throw new ValidationException("Unknown split strategy: " + key);
        }
        return s;
    }

    public static Collection<SplitStrategy> all() {
        return Collections.unmodifiableCollection(STRATEGIES.values());
    }
}
