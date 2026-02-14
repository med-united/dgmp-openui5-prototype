package de.servicehealth.epa.medication.model;

import java.util.List;

/**
 * Represents a detected duplicate match during medication addition.
 */
public class DuplicateMatch {

    private boolean isDuplicate;
    private String matchType; // "PZN", "ATC", "ASK"
    private MedicationPlanEntry existingEntry;
    private List<MedicationPlanEntry> conflictingEntries;

    public DuplicateMatch() {
    }

    public DuplicateMatch(boolean isDuplicate, String matchType, MedicationPlanEntry existingEntry) {
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

    public MedicationPlanEntry getExistingEntry() {
        return existingEntry;
    }

    public void setExistingEntry(MedicationPlanEntry existingEntry) {
        this.existingEntry = existingEntry;
    }

    public List<MedicationPlanEntry> getConflictingEntries() {
        return conflictingEntries;
    }

    public void setConflictingEntries(List<MedicationPlanEntry> conflictingEntries) {
        this.conflictingEntries = conflictingEntries;
    }
}
