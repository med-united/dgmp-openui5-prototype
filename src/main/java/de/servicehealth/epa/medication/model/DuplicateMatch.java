package de.servicehealth.epa.medication.model;

/**
 * Result of a duplicate-detection check against the in-memory plan bundle.
 */
public class DuplicateMatch {

    private final boolean duplicate;
    private final String matchType; // "PZN", "ATC", or null

    public DuplicateMatch(boolean duplicate, String matchType) {
        this.duplicate = duplicate;
        this.matchType = matchType;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public String getMatchType() {
        return matchType;
    }
}
