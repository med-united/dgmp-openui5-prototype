package de.servicehealth.epa.medication.model;

import java.time.Instant;
import java.util.List;

/**
 * Represents the state of the Medication Plan at a specific point in time.
 * Used for optimistic locking and versioning.
 */
public class EMPChronologyProvenance {
    private String id;
    private Instant recorded;
    private List<MedicationRequest> snapshot;

    public EMPChronologyProvenance() {
        this.recorded = Instant.now();
    }

    public EMPChronologyProvenance(String id, List<MedicationRequest> snapshot) {
        this.id = id;
        this.recorded = Instant.now();
        this.snapshot = snapshot;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getRecorded() {
        return recorded;
    }

    public void setRecorded(Instant recorded) {
        this.recorded = recorded;
    }

    public List<MedicationRequest> getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(List<MedicationRequest> snapshot) {
        this.snapshot = snapshot;
    }
}
