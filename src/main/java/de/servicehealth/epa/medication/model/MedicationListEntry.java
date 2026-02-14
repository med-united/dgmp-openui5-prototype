package de.servicehealth.epa.medication.model;

import java.time.LocalDateTime;

/**
 * Entry in the eML (electronic Medication List) - historical prescription or
 * dispensement record.
 * Corresponds to FHIR MedicationStatement per ePA Medication Service IG v1.3.0
 */
public class MedicationListEntry extends MedicationEntry {

    private String entryType; // "prescription" or "dispensement"
    private LocalDateTime authoredDate; // When prescription/dispensement occurred
    private String prescriber; // Prescribing physician name
    private String pharmacy; // Dispensing pharmacy name (for dispensements)
    private String linkedToPlanId; // Reference to linked eMP entry (optional)

    // Constructors

    public MedicationListEntry() {
        super();
    }

    public MedicationListEntry(String id, String pzn, String medicationName, String activeIngredient,
            String entryType, LocalDateTime authoredDate) {
        super(id, pzn, medicationName, activeIngredient);
        this.entryType = entryType;
        this.authoredDate = authoredDate;
    }

    // Getters and Setters

    public String getEntryType() {
        return entryType;
    }

    public void setEntryType(String entryType) {
        this.entryType = entryType;
    }

    public LocalDateTime getAuthoredDate() {
        return authoredDate;
    }

    public void setAuthoredDate(LocalDateTime authoredDate) {
        this.authoredDate = authoredDate;
    }

    public String getPrescriber() {
        return prescriber;
    }

    public void setPrescriber(String prescriber) {
        this.prescriber = prescriber;
    }

    public String getPharmacy() {
        return pharmacy;
    }

    public void setPharmacy(String pharmacy) {
        this.pharmacy = pharmacy;
    }

    public String getLinkedToPlanId() {
        return linkedToPlanId;
    }

    public void setLinkedToPlanId(String linkedToPlanId) {
        this.linkedToPlanId = linkedToPlanId;
    }

    // Business methods

    public boolean isPrescription() {
        return "prescription".equals(entryType);
    }

    public boolean isDispensement() {
        return "dispensement".equals(entryType);
    }

    public boolean isLinkedToMedicationPlan() {
        return linkedToPlanId != null && !linkedToPlanId.isEmpty();
    }

    @Override
    public String toString() {
        return "MedicationListEntry{" +
                "id='" + getId() + '\'' +
                ", pzn='" + getPzn() + '\'' +
                ", medicationName='" + getMedicationName() + '\'' +
                ", entryType='" + entryType + '\'' +
                ", authoredDate=" + authoredDate +
                ", prescriber='" + prescriber + '\'' +
                ", pharmacy='" + pharmacy + '\'' +
                '}';
    }
}
