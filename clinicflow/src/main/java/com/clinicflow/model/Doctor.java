package com.clinicflow.model;

/**
 * Represents an attending physician or medical doctor in the clinic.
 * Extends {@link Person} demonstrating polymorphism and inheritance.
 */
public class Doctor extends Person {

    private String specialty;
    private String roomNumber;
    private String contactNumber;
    private boolean available;

    public Doctor(Long id, String name, Gender gender, int age, String specialty, String roomNumber) {
        this(id, name, gender, age, specialty, roomNumber, "", true);
    }

    public Doctor(Long id, String name, Gender gender, int age, String specialty, String roomNumber,
                  String contactNumber, boolean available) {
        super(id, name, gender, age);
        if (specialty == null || specialty.trim().isEmpty()) {
            throw new IllegalArgumentException("Specialty cannot be null or blank");
        }
        this.specialty = specialty.trim();
        this.roomNumber = roomNumber != null ? roomNumber.trim() : "TBD";
        this.contactNumber = contactNumber != null ? contactNumber.trim() : "";
        this.available = available;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        if (specialty == null || specialty.trim().isEmpty()) {
            throw new IllegalArgumentException("Specialty cannot be null or blank");
        }
        this.specialty = specialty.trim();
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber != null ? contactNumber.trim() : "";
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public String getRoleDescription() {
        return "Dr. " + getName() + " (" + specialty + ", Room " + roomNumber + ")";
    }
}
