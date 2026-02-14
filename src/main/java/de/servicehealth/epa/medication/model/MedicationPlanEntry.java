package de.servicehealth.epa.medication.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Entry in the electronic Medication Plan (eMP).
 * Represents a current therapy instruction.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MedicationPlanEntry extends MedicationEntry {

    // Status: active, paused, planned
    private String status;

    // Optional notes
    private String note;

    // Reason for pausing the medication
    private String reasonForPause;

    // Entry source (e.g. "manual", "prescription")
    private String entrySource;

    private java.time.Instant authoredDate;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getReasonForPause() {
        return reasonForPause;
    }

    public void setReasonForPause(String reasonForPause) {
        this.reasonForPause = reasonForPause;
    }

    public String getEntrySource() {
        return entrySource;
    }

    public void setEntrySource(String entrySource) {
        this.entrySource = entrySource;
    }

    public java.time.Instant getAuthoredDate() {
        return authoredDate;
    }

    public void setAuthoredDate(java.time.Instant authoredDate) {
        this.authoredDate = authoredDate;
    }

    @Override
    public String toString() {
        return "MedicationPlanEntry{" +
                "id='" + getId() + '\'' +
                ", medicationName='" + getMedicationName() + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
