package com.clinicflow.model;

/**
 * Represents a registered clinic patient.
 * Extends {@link Person} to demonstrate OOP Inheritance, Encapsulation, and Constructors.
 */
public class Patient extends Person {

    private boolean scholarship;
    private boolean hipertension;
    private boolean diabetes;
    private boolean alcoholism;
    private int handicap;
    private String contactNumber;

    /**
     * Primary constructor with medical history flags matching dataset attributes.
     */
    public Patient(Long id, String name, Gender gender, int age,
                   boolean scholarship, boolean hipertension, boolean diabetes,
                   boolean alcoholism, int handicap, String contactNumber) {
        super(id, name, gender, age);
        if (handicap < 0 || handicap > 4) {
            throw new IllegalArgumentException("Handicap score must be between 0 and 4, got: " + handicap);
        }
        this.scholarship = scholarship;
        this.hipertension = hipertension;
        this.diabetes = diabetes;
        this.alcoholism = alcoholism;
        this.handicap = handicap;
        this.contactNumber = contactNumber != null ? contactNumber.trim() : "";
    }

    public Patient(Long id, String name, Gender gender, int age,
                   boolean scholarship, boolean hipertension, boolean diabetes,
                   boolean alcoholism, int handicap) {
        this(id, name, gender, age, scholarship, hipertension, diabetes, alcoholism, handicap, "");
    }

    /**
     * Overloaded convenience constructor for standard healthy patient.
     */
    public Patient(Long id, String name, Gender gender, int age) {
        this(id, name, gender, age, false, false, false, false, 0, "");
    }

    public Patient(Long id, String name, Gender gender, int age, String contactNumber) {
        this(id, name, gender, age, false, false, false, false, 0, contactNumber);
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber != null ? contactNumber.trim() : "";
    }

    public boolean isScholarship() {
        return scholarship;
    }

    public void setScholarship(boolean scholarship) {
        this.scholarship = scholarship;
    }

    public boolean isHipertension() {
        return hipertension;
    }

    public void setHipertension(boolean hipertension) {
        this.hipertension = hipertension;
    }

    public boolean isDiabetes() {
        return diabetes;
    }

    public void setDiabetes(boolean diabetes) {
        this.diabetes = diabetes;
    }

    public boolean isAlcoholism() {
        return alcoholism;
    }

    public void setAlcoholism(boolean alcoholism) {
        this.alcoholism = alcoholism;
    }

    public int getHandicap() {
        return handicap;
    }

    public void setHandicap(int handicap) {
        if (handicap < 0 || handicap > 4) {
            throw new IllegalArgumentException("Handicap score must be between 0 and 4, got: " + handicap);
        }
        this.handicap = handicap;
    }

    @Override
    public String getRoleDescription() {
        return "Patient [ID: " + getId() + ", Age: " + getAge() + ", Chronic Conditions: " +
                (hasChronicConditions() ? "Yes" : "None") + "]";
    }

    public boolean hasChronicConditions() {
        return hipertension || diabetes || alcoholism || handicap > 0;
    }
}
