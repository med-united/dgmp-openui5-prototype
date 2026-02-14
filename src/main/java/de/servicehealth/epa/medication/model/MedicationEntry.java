package de.servicehealth.epa.medication.model;

import java.util.Objects;

/**
 * Base entity for medication entries in either eML or eMP.
 * Not instantiated directly - use MedicationListEntry or MedicationPlanEntry.
 */
public abstract class MedicationEntry {

    private String id;
    private String pzn; // Pharmazentralnummer (8 digits)
    private String medicationName; // Trade name
    private String activeIngredient; // Wirkstoff
    private String strength; // e.g., "5mg", "10mg/ml"
    private String dosageText; // Free-text dosage
    private String dosageStructured; // Pattern: \d-\d-\d-\d (morning-noon-evening-night)
    private String intakeInstructions; // e.g., "vor dem Essen"
    private String indication; // Behandlungsgrund
    private String atcCode; // WHO ATC code
    private String askCode; // German ASK code

    // Constructors

    protected MedicationEntry() {
        // For Jackson deserialization
    }

    protected MedicationEntry(String id, String pzn, String medicationName, String activeIngredient) {
        this.id = id;
        this.pzn = pzn;
        this.medicationName = medicationName;
        this.activeIngredient = activeIngredient;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getActiveIngredient() {
        return activeIngredient;
    }

    public void setActiveIngredient(String activeIngredient) {
        this.activeIngredient = activeIngredient;
    }

    public String getStrength() {
        return strength;
    }

    public void setStrength(String strength) {
        this.strength = strength;
    }

    public String getDosageText() {
        return dosageText;
    }

    public void setDosageText(String dosageText) {
        this.dosageText = dosageText;
    }

    public String getDosageStructured() {
        return dosageStructured;
    }

    public void setDosageStructured(String dosageStructured) {
        this.dosageStructured = dosageStructured;
    }

    public String getIntakeInstructions() {
        return intakeInstructions;
    }

    public void setIntakeInstructions(String intakeInstructions) {
        this.intakeInstructions = intakeInstructions;
    }

    public String getIndication() {
        return indication;
    }

    public void setIndication(String indication) {
        this.indication = indication;
    }

    public String getAtcCode() {
        return atcCode;
    }

    public void setAtcCode(String atcCode) {
        this.atcCode = atcCode;
    }

    public String getAskCode() {
        return askCode;
    }

    public void setAskCode(String askCode) {
        this.askCode = askCode;
    }

    // Validation helper

    public boolean isValid() {
        if (pzn == null || !pzn.matches("\\d{8}")) {
            return false; // PZN must be 8 digits
        }
        if (dosageStructured != null && !dosageStructured.matches("\\d-\\d-\\d-\\d")) {
            return false; // Structured dosage must match pattern
        }
        if (dosageText == null && dosageStructured == null) {
            return false; // At least one dosage type required
        }
        return true;
    }

    // equals, hashCode, toString

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof MedicationEntry))
            return false;
        MedicationEntry that = (MedicationEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "id='" + id + '\'' +
                ", pzn='" + pzn + '\'' +
                ", medicationName='" + medicationName + '\'' +
                ", activeIngredient='" + activeIngredient + '\'' +
                '}';
    }
}
