package de.servicehealth.epa.patient.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Patient domain model representing insured person (Versicherter)
 * Primary identifier: KVNR (Krankenversichertennummer)
 */
public class Patient {

    private String kvnr; // 10 alphanumeric characters
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String insuranceType; // GKV, PKV

    public Patient() {
    }

    public Patient(String kvnr, String firstName, String lastName, LocalDate dateOfBirth, String insuranceType) {
        this.kvnr = kvnr;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.insuranceType = insuranceType;
    }

    public String getKvnr() {
        return kvnr;
    }

    public void setKvnr(String kvnr) {
        this.kvnr = kvnr;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getInsuranceType() {
        return insuranceType;
    }

    public void setInsuranceType(String insuranceType) {
        this.insuranceType = insuranceType;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Patient patient = (Patient) o;
        return Objects.equals(kvnr, patient.kvnr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kvnr);
    }

    @Override
    public String toString() {
        return "Patient{" +
                "kvnr='" + kvnr + '\'' +
                ", name='" + getFullName() + '\'' +
                ", dob=" + dateOfBirth +
                '}';
    }
}
