package de.servicehealth.epa.medication.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate representing the electronic Medication Plan (eMP).
 * Contains a list of therapy entries grouped by status.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MedicationPlan {

    private String kvnr;
    private List<MedicationRequest> entries = new ArrayList<>();
    private int version;
    private Instant lastUpdated;

    public String getKvnr() {
        return kvnr;
    }

    public void setKvnr(String kvnr) {
        this.kvnr = kvnr;
    }

    public List<MedicationRequest> getEntries() {
        return entries;
    }

    public void setEntries(List<MedicationRequest> entries) {
        this.entries = entries;
    }

    public void addEntry(MedicationRequest entry) {
        this.entries.add(entry);
    }

    public int getEntryCount() {
        return entries.size();
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    private String chronologyId;

    public String getChronologyId() {
        return chronologyId;
    }

    public void setChronologyId(String chronologyId) {
        this.chronologyId = chronologyId;
    }
}
