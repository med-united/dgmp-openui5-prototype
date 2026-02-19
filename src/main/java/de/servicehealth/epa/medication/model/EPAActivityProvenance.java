package de.servicehealth.epa.medication.model;

import java.time.Instant;

/**
 * Audit log for activities performed on the Medication Service.
 */
public class EPAActivityProvenance {
    private String id;
    private Instant recorded;
    private String agent; // Organization from X-Requesting-Organization header
    private String entityReference; // Reference to the modified entity

    public EPAActivityProvenance() {
        this.recorded = Instant.now();
    }

    public EPAActivityProvenance(String id, String agent, String entityReference) {
        this.id = id;
        this.agent = agent;
        this.entityReference = entityReference;
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

    public String getAgent() {
        return agent;
    }

    public void setAgent(String agent) {
        this.agent = agent;
    }

    public String getEntityReference() {
        return entityReference;
    }

    public void setEntityReference(String entityReference) {
        this.entityReference = entityReference;
    }
}
