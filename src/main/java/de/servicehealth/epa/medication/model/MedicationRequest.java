package de.servicehealth.epa.medication.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Entry in the electronic Medication Plan (eMP).
 * Represents a current therapy instruction.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MedicationRequest extends MedicationEntry {

    // Status: active, paused, planned
    private String status;

    // Optional notes
    private String note;

    // Reason for pausing the medication
    private String reasonForPause;

    // Entry source (e.g. "manual", "prescription")
    private String entrySource;

    private java.time.Instant authoredDate;

    // IDs of eML entries linked to this therapy
    private java.util.List<String> linkedEmlIds = new java.util.ArrayList<>();

    // New LINK Logic
    private String medicationPlanIdentifier; // UUID identifying this plan item

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public java.util.List<String> getLinkedEmlIds() {
        return linkedEmlIds;
    }

    public void setLinkedEmlIds(java.util.List<String> linkedEmlIds) {
        this.linkedEmlIds = linkedEmlIds;
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

    // Type of entry (e.g. "manual", "dispensed", "prescribed") - Alias/Related to
    // Source
    private String entryType;

    public String getEntryType() {
        return entryType;
    }

    public void setEntryType(String entryType) {
        this.entryType = entryType;
    }

    public java.time.Instant getAuthoredDate() {
        return authoredDate;
    }

    public void setAuthoredDate(java.time.Instant authoredDate) {
        this.authoredDate = authoredDate;
    }

    public String getMedicationPlanIdentifier() {
        return medicationPlanIdentifier;
    }

    public void setMedicationPlanIdentifier(String medicationPlanIdentifier) {
        this.medicationPlanIdentifier = medicationPlanIdentifier;
    }

    @Override
    public String toString() {
        return "MedicationRequest{" +
                "id='" + getId() + '\'' +
                ", medicationName='" + getMedicationName() + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
