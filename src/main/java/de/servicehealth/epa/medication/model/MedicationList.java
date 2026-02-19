package de.servicehealth.epa.medication.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Aggregate containing all eML entries for a patient.
 * Entries are ordered by authoredDate descending (most recent first).
 */
public class MedicationList {

    private String kvnr;
    private List<MedicationStatement> entries;
    private LocalDateTime lastUpdated;

    // Constructors

    public MedicationList() {
        this.entries = new ArrayList<>();
    }

    public MedicationList(String kvnr) {
        this.kvnr = kvnr;
        this.entries = new ArrayList<>();
        this.lastUpdated = LocalDateTime.now();
    }

    // Getters and Setters

    public String getKvnr() {
        return kvnr;
    }

    public void setKvnr(String kvnr) {
        this.kvnr = kvnr;
    }

    public List<MedicationStatement> getEntries() {
        return entries;
    }

    public void setEntries(List<MedicationStatement> entries) {
        this.entries = entries;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    // Business methods

    /**
     * Sort entries by authoredDate descending (newest first).
     * This is the default display order for eML.
     */
    public void sortByAuthoredDateDesc() {
        if (entries != null) {
            entries.sort(Comparator.comparing(MedicationStatement::getAuthoredDate).reversed());
        }
    }

    /**
     * Check if medication list is empty
     */
    public boolean isEmpty() {
        return entries == null || entries.isEmpty();
    }

    /**
     * Get count of entries
     */
    public int getEntryCount() {
        return entries == null ? 0 : entries.size();
    }

    @Override
    public String toString() {
        return "MedicationList{" +
                "kvnr='" + kvnr + '\'' +
                ", entryCount=" + getEntryCount() +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
