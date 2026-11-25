package com.clinicflow.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Core domain entity representing a clinic appointment.
 * Encapsulates patient details, scheduling timestamps, neighborhood, attendance status,
 * and ML-driven risk prediction.
 */
public class Appointment implements Identifiable<Long> {

    private final Long appointmentId;
    private final Patient patient;
    private Doctor doctor;
    private final LocalDateTime scheduledDay;
    private final LocalDate appointmentDay;
    private LocalTime appointmentTime;
    private String queueToken;
    private String neighbourhood;
    private boolean smsReceived;
    private AttendanceStatus attendanceStatus;
    private PredictionResult predictionResult;
    private String notes;
    private LocalDateTime checkInTime;
    private String reminderStatus;

    /**
     * Complete constructor supporting clinical scheduling and smart queue operations.
     */
    public Appointment(Long appointmentId, Patient patient, Doctor doctor,
                       LocalDateTime scheduledDay, LocalDate appointmentDay, LocalTime appointmentTime,
                       String queueToken, String neighbourhood, boolean smsReceived,
                       AttendanceStatus attendanceStatus, String notes,
                       LocalDateTime checkInTime, String reminderStatus) {
        if (appointmentId == null) {
            throw new IllegalArgumentException("Appointment ID cannot be null");
        }
        if (patient == null) {
            throw new IllegalArgumentException("Patient cannot be null");
        }
        if (scheduledDay == null) {
            throw new IllegalArgumentException("ScheduledDay timestamp cannot be null");
        }
        if (appointmentDay == null) {
            throw new IllegalArgumentException("AppointmentDay date cannot be null");
        }
        if (neighbourhood == null || neighbourhood.trim().isEmpty()) {
            throw new IllegalArgumentException("Neighbourhood cannot be null or empty");
        }
        this.appointmentId = appointmentId;
        this.patient = patient;
        this.doctor = doctor;
        this.scheduledDay = scheduledDay;
        this.appointmentDay = appointmentDay;
        this.appointmentTime = appointmentTime != null ? appointmentTime : LocalTime.of(9, 0);
        this.queueToken = queueToken != null && !queueToken.trim().isEmpty() ? queueToken.trim() : "Q-" + String.format("%03d", (Math.abs(appointmentId) % 1000) + 1);
        this.neighbourhood = neighbourhood.trim();
        this.smsReceived = smsReceived;
        this.attendanceStatus = attendanceStatus != null ? attendanceStatus : AttendanceStatus.SCHEDULED;
        this.notes = notes != null ? notes.trim() : "";
        this.checkInTime = checkInTime;
        this.reminderStatus = reminderStatus != null ? reminderStatus.trim() : "PENDING";
    }

    /**
     * Constructor for historical or standard appointment scheduling.
     */
    public Appointment(Long appointmentId, Patient patient, Doctor doctor,
                       LocalDateTime scheduledDay, LocalDate appointmentDay,
                       String neighbourhood, boolean smsReceived, AttendanceStatus attendanceStatus) {
        this(appointmentId, patient, doctor, scheduledDay, appointmentDay,
                LocalTime.of(9, 0), "Q-" + String.format("%03d", (Math.abs(appointmentId) % 1000) + 1),
                neighbourhood, smsReceived, attendanceStatus, "", null, "PENDING");
    }

    /**
     * Overloaded constructor for booking a new appointment in the clinic system.
     */
    public Appointment(Long appointmentId, Patient patient, Doctor doctor,
                       LocalDateTime scheduledDay, LocalDate appointmentDay, String neighbourhood) {
        this(appointmentId, patient, doctor, scheduledDay, appointmentDay, neighbourhood, false, AttendanceStatus.SCHEDULED);
    }

    /**
     * Overloaded constructor for booking with explicit time, notes, and token.
     */
    public Appointment(Long appointmentId, Patient patient, Doctor doctor,
                       LocalDateTime scheduledDay, LocalDate appointmentDay, LocalTime appointmentTime,
                       String queueToken, String neighbourhood, String notes) {
        this(appointmentId, patient, doctor, scheduledDay, appointmentDay, appointmentTime,
                queueToken, neighbourhood, false, AttendanceStatus.SCHEDULED, notes, null, "PENDING");
    }

    @Override
    public Long getId() {
        return appointmentId;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public Patient getPatient() {
        return patient;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public void setDoctor(Doctor doctor) {
        this.doctor = doctor;
    }

    public LocalDateTime getScheduledDay() {
        return scheduledDay;
    }

    public LocalDate getAppointmentDay() {
        return appointmentDay;
    }

    public String getNeighbourhood() {
        return neighbourhood;
    }

    public void setNeighbourhood(String neighbourhood) {
        if (neighbourhood == null || neighbourhood.trim().isEmpty()) {
            throw new IllegalArgumentException("Neighbourhood cannot be null or empty");
        }
        this.neighbourhood = neighbourhood.trim();
    }

    public boolean isSmsReceived() {
        return smsReceived;
    }

    public void setSmsReceived(boolean smsReceived) {
        this.smsReceived = smsReceived;
    }

    public AttendanceStatus getAttendanceStatus() {
        return attendanceStatus;
    }

    public void setAttendanceStatus(AttendanceStatus attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }

    public PredictionResult getPredictionResult() {
        return predictionResult;
    }

    public void setPredictionResult(PredictionResult predictionResult) {
        this.predictionResult = predictionResult;
    }

    public LocalTime getAppointmentTime() {
        return appointmentTime;
    }

    public void setAppointmentTime(LocalTime appointmentTime) {
        this.appointmentTime = appointmentTime != null ? appointmentTime : LocalTime.of(9, 0);
    }

    public String getQueueToken() {
        return queueToken;
    }

    public void setQueueToken(String queueToken) {
        this.queueToken = queueToken;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes != null ? notes.trim() : "";
    }

    public LocalDateTime getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(LocalDateTime checkInTime) {
        this.checkInTime = checkInTime;
    }

    public String getReminderStatus() {
        return reminderStatus;
    }

    public void setReminderStatus(String reminderStatus) {
        this.reminderStatus = reminderStatus != null ? reminderStatus.trim() : "PENDING";
    }

    /**
     * Computes the waiting duration in days between booking date and appointment date.
     * Note: ScheduledDay time is truncated to date for lead-day calculation.
     */
    public long getLeadDays() {
        return ChronoUnit.DAYS.between(scheduledDay.toLocalDate(), appointmentDay);
    }

    /**
     * Day of week when the appointment was scheduled.
     */
    public String getScheduledDayOfWeek() {
        return scheduledDay.getDayOfWeek().name();
    }

    /**
     * Day of week on which the actual appointment occurs.
     */
    public String getAppointmentDayOfWeek() {
        return appointmentDay.getDayOfWeek().name();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Appointment that = (Appointment) o;
        return Objects.equals(appointmentId, that.appointmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appointmentId);
    }

    @Override
    public String toString() {
        return "Appointment{" +
                "id=" + appointmentId +
                ", patient=" + patient.getName() + " (ID: " + patient.getId() + ")" +
                ", appointmentDay=" + appointmentDay +
                ", leadDays=" + getLeadDays() +
                ", neighbourhood='" + neighbourhood + '\'' +
                ", status=" + attendanceStatus +
                ", prediction=" + predictionResult +
                '}';
    }
}
