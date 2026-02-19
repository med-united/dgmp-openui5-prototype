package de.servicehealth.epa.medication.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single item in the reconciliation view, comparing an eML entry
 * (history) with eMP entries (plan).
 * Enhanced with match type and confidence for split-view UI.
 */
public class ReconciliationItem {

    private String pzn;
    private String medicationName;
    private String atcCode;

    private MedicationStatement emlEntry; // The historical entry (source)
    private MedicationRequest empEntry; // The matching plan entry (target), if any

    // Enhanced matching fields
    private MatchType matchType; // EXACT_MATCH, PROBABLE_MATCH, or ORPHAN
    private int matchConfidence; // 0-100, confidence score
    private String matchReason; // Human-readable explanation
    private List<String> missingFields; // Fields needed when transferring to eMP

    // Legacy fields (kept for backward compatibility)
    private boolean inPlan; // True if a matching entry exists in eMP
    private boolean discrepancy; // True if there are differences (e.g. dosage)
    private String discrepancyDetails; // Description of differences

    public ReconciliationItem() {
        this.missingFields = new ArrayList<>();
    }

    public ReconciliationItem(MedicationStatement emlEntry, MedicationRequest empEntry) {
        this();
        this.emlEntry = emlEntry;
        this.empEntry = empEntry;

        // Common fields derived from eML entry as primary source
        if (emlEntry != null) {
            this.pzn = emlEntry.getPzn();
            this.medicationName = emlEntry.getMedicationName();
            this.atcCode = emlEntry.getAtcCode();
        } else if (empEntry != null) {
            this.pzn = empEntry.getPzn();
            this.medicationName = empEntry.getMedicationName();
            this.atcCode = empEntry.getAtcCode();
        }

        this.inPlan = (empEntry != null);
        determineDiscrepancy();
    }

    private void determineDiscrepancy() {
        if (!inPlan) {
            this.discrepancy = true;
            this.discrepancyDetails = "Missing in Medication Plan";
            return;
        }

        // If in plan, check for differences in key fields (e.g., dosage)
        // For prototype, we assume if it exists, it's reconciled, unless specific
        // fields differ
        // Simplification for now: existence = reconciled.
        this.discrepancy = false;
        this.discrepancyDetails = null;
    }

    // Getters and Setters

    public String getPzn() {
        return pzn;
    }

    public void setPzn(String pzn) {
        this.pzn = pzn;
    }

    public String getMedicationName() {
        return medicationName;
    }

    public void setMedicationName(String medicationName) {
        this.medicationName = medicationName;
    }

    public String getAtcCode() {
        return atcCode;
    }

    public void setAtcCode(String atcCode) {
        this.atcCode = atcCode;
    }

    public MedicationStatement getEmlEntry() {
        return emlEntry;
    }

    public void setEmlEntry(MedicationStatement emlEntry) {
        this.emlEntry = emlEntry;
    }

    public MedicationRequest getEmpEntry() {
        return empEntry;
    }

    public void setEmpEntry(MedicationRequest empEntry) {
        this.empEntry = empEntry;
    }

    public boolean isInPlan() {
        return inPlan;
    }

    public void setInPlan(boolean inPlan) {
        this.inPlan = inPlan;
    }

    public boolean isDiscrepancy() {
        return discrepancy;
    }

    public void setDiscrepancy(boolean discrepancy) {
        this.discrepancy = discrepancy;
    }

    public String getDiscrepancyDetails() {
        return discrepancyDetails;
    }

    public void setDiscrepancyDetails(String discrepancyDetails) {
        this.discrepancyDetails = discrepancyDetails;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(MatchType matchType) {
        this.matchType = matchType;
    }

    public int getMatchConfidence() {
        return matchConfidence;
    }

    public void setMatchConfidence(int matchConfidence) {
        this.matchConfidence = matchConfidence;
    }

    public String getMatchReason() {
        return matchReason;
    }

    public void setMatchReason(String matchReason) {
        this.matchReason = matchReason;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(List<String> missingFields) {
        this.missingFields = missingFields;
    }
}
