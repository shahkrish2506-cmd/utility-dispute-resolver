package com.utilityresolver.model.utility;

/** The supported bill categories (four utilities + groceries + miscellaneous). Acts as a simple factory for Utility objects. */
public enum UtilityType {
    ELECTRICITY, WATER, GAS, INTERNET, GROCERIES, MISCELLANEOUS;

    /** Factory method: returns the matching Utility subclass (polymorphic creation). */
    public Utility create() {
        switch (this) {
            case ELECTRICITY: return new Electricity();
            case WATER: return new Water();
            case GAS: return new Gas();
            case INTERNET: return new Internet();
            case GROCERIES: return new Groceries();
            case MISCELLANEOUS: return new Miscellaneous();
            default: throw new IllegalStateException("Unknown utility " + this);
        }
    }

    public static UtilityType parse(String s) {
        return UtilityType.valueOf(s.trim().toUpperCase());
    }
}
