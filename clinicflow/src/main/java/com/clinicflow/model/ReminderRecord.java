package com.clinicflow.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Domain entity representing a simulated patient appointment reminder.
 * Used for academic demonstration of ML-driven proactive patient outreach.
 * Persisted in data/app/reminders.csv.
 */
public class ReminderRecord implements Identifiable<Long>, Serializable {

    private static final long serialVersionUID = 1L;

    private final Long reminderId;
    private final Long appointmentId;
    private final String patientName;
    private final String patientContact;
    private final LocalDate appointmentDate;
    private final LocalTime appointmentTime;
    private final String doctorName;
    private final RiskCategory riskCategory;
    private final ReminderAction recommendedAction;
    private String status; // "PENDING", "SENT"
    private LocalDateTime sentTimestamp;
    private String notes;

    public ReminderRecord(Long reminderId, Long appointmentId, String patientName,
                          String patientContact, LocalDate appointmentDate, LocalTime appointmentTime,
                          String doctorName, RiskCategory riskCategory, ReminderAction recommendedAction,
                          String status, LocalDateTime sentTimestamp, String notes) {
        if (reminderId == null) {
            throw new IllegalArgumentException("Reminder ID cannot be null");
        }
        if (appointmentId == null) {
            throw new IllegalArgumentException("Appointment ID cannot be null");
        }
        this.reminderId = reminderId;
        this.appointmentId = appointmentId;
        this.patientName = patientName != null ? patientName.trim() : "Unknown";
        this.patientContact = patientContact != null ? patientContact.trim() : "N/A";
        this.appointmentDate = appointmentDate != null ? appointmentDate : LocalDate.now();
        this.appointmentTime = appointmentTime != null ? appointmentTime : LocalTime.of(9, 0);
        this.doctorName = doctorName != null ? doctorName.trim() : "Attending Physician";
        this.riskCategory = riskCategory != null ? riskCategory : RiskCategory.LOW;
        this.recommendedAction = recommendedAction != null ? recommendedAction : ReminderAction.STANDARD;
        this.status = status != null ? status.trim() : "PENDING";
        this.sentTimestamp = sentTimestamp;
        this.notes = notes != null ? notes.trim() : "Simulated reminder for academic demonstration";
    }

    public static ReminderRecord fromAppointment(Long reminderId, Appointment appointment) {
        if (appointment == null) {
            throw new IllegalArgumentException("Appointment cannot be null");
        }
        RiskCategory risk = appointment.getPredictionResult() != null ?
                appointment.getPredictionResult().getRiskCategory() : RiskCategory.LOW;
        ReminderAction action = appointment.getPredictionResult() != null ?
                appointment.getPredictionResult().getSuggestedAction() : ReminderAction.STANDARD;

        String patientContact = appointment.getPatient() != null ? appointment.getPatient().getContactNumber() : "N/A";
        String doctorName = appointment.getDoctor() != null ? appointment.getDoctor().getName() : "Attending Physician";

        return new ReminderRecord(
                reminderId,
                appointment.getAppointmentId(),
                appointment.getPatient().getName(),
                patientContact,
                appointment.getAppointmentDay(),
                appointment.getAppointmentTime(),
                doctorName,
                risk,
                action,
                "PENDING",
                null,
                "Academic simulation - non-punitive confirmation"
        );
    }

    @Override
    public Long getId() {
        return reminderId;
    }

    public Long getReminderId() {
        return reminderId;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public String getPatientName() {
        return patientName;
    }

    public String getPatientContact() {
        return patientContact;
    }

    public LocalDate getAppointmentDate() {
        return appointmentDate;
    }

    public LocalTime getAppointmentTime() {
        return appointmentTime;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public RiskCategory getRiskCategory() {
        return riskCategory;
    }

    public ReminderAction getRecommendedAction() {
        return recommendedAction;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSentTimestamp() {
        return sentTimestamp;
    }

    public void setSentTimestamp(LocalDateTime sentTimestamp) {
        this.sentTimestamp = sentTimestamp;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void markSimulatedSent() {
        this.status = "SENT";
        this.sentTimestamp = LocalDateTime.now();
        this.notes = "Simulated " + recommendedAction.name() + " recorded at " + sentTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReminderRecord that = (ReminderRecord) o;
        return Objects.equals(reminderId, that.reminderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reminderId);
    }
}
