package de.servicehealth.epa.medication.model;

import java.util.List;

/**
 * Represents a detected duplicate match during medication addition.
 */
public class DuplicateMatch {

    private boolean isDuplicate;
    private String matchType; // "PZN", "ATC", "ASK"
    private MedicationRequest existingEntry;
    private List<MedicationRequest> conflictingEntries;

    public DuplicateMatch() {
    }

    public DuplicateMatch(boolean isDuplicate, String matchType, MedicationRequest existingEntry) {
        this.isDuplicate = isDuplicate;
        this.matchType = matchType;
        this.existingEntry = existingEntry;
    }

    public boolean isDuplicate() {
        return isDuplicate;
    }

    public void setDuplicate(boolean duplicate) {
        isDuplicate = duplicate;
    }

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    public MedicationRequest getExistingEntry() {
        return existingEntry;
    }

    public void setExistingEntry(MedicationRequest existingEntry) {
        this.existingEntry = existingEntry;
    }

    public List<MedicationRequest> getConflictingEntries() {
        return conflictingEntries;
    }

    public void setConflictingEntries(List<MedicationRequest> conflictingEntries) {
        this.conflictingEntries = conflictingEntries;
    }
}
