package de.servicehealth.epa.medication.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper class for hierarchical prescription/dispensation display in
 * reconciliation view.
 * Groups a prescription with its associated dispensations and calculates
 * reconciliation status.
 */
public class PrescriptionGroup {

    private MedicationListEntry prescription;
    private List<MedicationListEntry> dispensations;
    private ReconciliationStatus status;
    private String empReference; // Reference to linked eMP entry (if LINKED)
    private String matchReason; // Human-readable explanation of status

    public PrescriptionGroup() {
        this.dispensations = new ArrayList<>();
    }

    public PrescriptionGroup(MedicationListEntry prescription) {
        this.prescription = prescription;
        this.dispensations = new ArrayList<>();
    }

    // Getters and Setters

    public String getId() {
        return prescription != null ? prescription.getId() : null;
    }

    public MedicationListEntry getPrescription() {
        return prescription;
    }

    public void setPrescription(MedicationListEntry prescription) {
        this.prescription = prescription;
    }

    public List<MedicationListEntry> getDispensations() {
        return dispensations;
    }

    public void setDispensations(List<MedicationListEntry> dispensations) {
        this.dispensations = dispensations;
    }

    public void addDispensation(MedicationListEntry dispensation) {
        this.dispensations.add(dispensation);
    }

    public ReconciliationStatus getStatus() {
        return status;
    }

    public void setStatus(ReconciliationStatus status) {
        this.status = status;
    }

    public String getEmpReference() {
        return empReference;
    }

    public void setEmpReference(String empReference) {
        this.empReference = empReference;
    }

    public String getMatchReason() {
        return matchReason;
    }

    public void setMatchReason(String matchReason) {
        this.matchReason = matchReason;
    }

    // Helper methods

    public boolean hasDispensations() {
        return dispensations != null && !dispensations.isEmpty();
    }

    public boolean isLinked() {
        return status == ReconciliationStatus.LINKED;
    }

    public boolean needsAction() {
        return status == ReconciliationStatus.ORPHAN_NEW ||
                status == ReconciliationStatus.UNLINKED_UPDATE;
    }

    // Delegating getters for UI Binding (Validation: Flattening hierarchy for
    // TreeTable)

    public String getMedicationName() {
        return prescription != null ? prescription.getMedicationName() : null;
    }

    public String getPzn() {
        return prescription != null ? prescription.getPzn() : null;
    }

    public String getAtcCode() {
        return prescription != null ? prescription.getAtcCode() : null;
    }

    public String getStrength() {
        return prescription != null ? prescription.getStrength() : null;
    }

    public String getDosageForm() {
        return prescription != null ? prescription.getDosageForm() : null;
    }

    public java.time.LocalDateTime getAuthoredDate() {
        return prescription != null ? prescription.getAuthoredDate() : null;
    }

    public String getEntryType() {
        return "prescription";
    }

    public String getPrescriberName() {
        return prescription != null ? prescription.getPrescriberName() : null;
    }
}
