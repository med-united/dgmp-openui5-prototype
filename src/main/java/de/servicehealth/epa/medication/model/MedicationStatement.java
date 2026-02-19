package de.servicehealth.epa.medication.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry in the eML (electronic Medication List) - historical prescription or
 * dispensement record.
 * Corresponds to FHIR MedicationStatement per ePA Medication Service IG v1.3.0
 */
public class MedicationStatement extends MedicationEntry {

    private String entryType; // "prescription" or "dispensement"
    private LocalDateTime authoredDate; // When prescription/dispensement occurred
    private String prescriber; // Prescribing physician name (DEPRECATED - use prescriberName)
    private String pharmacy; // Dispensing pharmacy name (DEPRECATED - use pharmacyName)
    private String linkedToPlanId; // Reference to linked eMP entry (optional)

    // dgMP-specific fields
    private String prescriberName; // Name of prescribing physician
    private String pharmacyName; // Name of dispensing pharmacy
    private String dosageForm; // "FTA", "Kapseln", "Tabletten", etc.
    private boolean substituted; // true if pharmacy substituted medication
    private String originalPrescribedPzn; // Original PZN from prescription (when substituted)
    private String basedOnReference; // Reference to prescription ID (for dispensations)

    // New LINK Logic
    private String medicationPlanIdentifier; // UUID identifying the plan item
    private String basedOn; // Reference to the MedicationRequest (eMP entry) ID

    // Hierarchical structure (for prescriptions)
    private List<MedicationStatement> dispensations; // Dispensations for this prescription

    // Constructors

    public MedicationStatement() {
        super();
    }

    public MedicationStatement(String id, String pzn, String medicationName, String activeIngredient,
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

    // dgMP getters/setters

    public String getPrescriberName() {
        return prescriberName;
    }

    public void setPrescriberName(String prescriberName) {
        this.prescriberName = prescriberName;
    }

    public String getPharmacyName() {
        return pharmacyName;
    }

    public void setPharmacyName(String pharmacyName) {
        this.pharmacyName = pharmacyName;
    }

    public String getDosageForm() {
        return dosageForm;
    }

    public void setDosageForm(String dosageForm) {
        this.dosageForm = dosageForm;
    }

    public boolean isSubstituted() {
        return substituted;
    }

    public void setSubstituted(boolean substituted) {
        this.substituted = substituted;
    }

    public String getOriginalPrescribedPzn() {
        return originalPrescribedPzn;
    }

    public void setOriginalPrescribedPzn(String originalPrescribedPzn) {
        this.originalPrescribedPzn = originalPrescribedPzn;
    }

    public String getBasedOnReference() {
        return basedOnReference;
    }

    public void setBasedOnReference(String basedOnReference) {
        this.basedOnReference = basedOnReference;
    }

    public List<MedicationStatement> getDispensations() {
        if (dispensations == null) {
            dispensations = new ArrayList<>();
        }
        return dispensations;
    }

    public void setDispensations(List<MedicationStatement> dispensations) {
        this.dispensations = dispensations;
    }

    public void addDispensation(MedicationStatement dispensation) {
        getDispensations().add(dispensation);
    }

    public String getMedicationPlanIdentifier() {
        return medicationPlanIdentifier;
    }

    public void setMedicationPlanIdentifier(String medicationPlanIdentifier) {
        this.medicationPlanIdentifier = medicationPlanIdentifier;
    }

    public String getBasedOn() {
        return basedOn;
    }

    public void setBasedOn(String basedOn) {
        this.basedOn = basedOn;
    }

    @Override
    public String toString() {
        return "MedicationStatement{" +
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
