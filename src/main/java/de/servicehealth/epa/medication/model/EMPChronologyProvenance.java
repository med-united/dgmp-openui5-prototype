package de.servicehealth.epa.medication.model;

import java.time.Instant;

/**
 * Tracks the current chronology ID of the Medication Plan.
 * Used for optimistic-locking (ifMatch) checks on mutating FHIR operations.
 */
public class EMPChronologyProvenance {
    private String id;
    private Instant recorded;

    public EMPChronologyProvenance() {
        this.recorded = Instant.now();
    }

    public EMPChronologyProvenance(String id) {
        this.id = id;
        this.recorded = Instant.now();
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
}
