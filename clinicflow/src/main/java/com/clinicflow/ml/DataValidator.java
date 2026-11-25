package com.clinicflow.ml;

import com.clinicflow.model.Appointment;
import com.clinicflow.model.Patient;
import com.clinicflow.util.ValidationException;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Validates clinical entities and appointment attributes against business and dataset constraints.
 */
public class DataValidator {

    public static final int MIN_AGE = 0;
    public static final int MAX_AGE = 120;
    public static final int MAX_HANDICAP = 4;

    /**
     * Validates a patient object.
     *
     * @param patient patient to validate
     * @throws ValidationException if any attribute violates constraints
     */
    public void validatePatient(Patient patient) {
        if (patient == null) {
            throw new ValidationException("Patient reference cannot be null");
        }
        if (patient.getId() == null || patient.getId() <= 0) {
            throw new ValidationException("Patient ID must be a positive number, got: " + patient.getId());
        }
        if (patient.getAge() < MIN_AGE || patient.getAge() > MAX_AGE) {
            throw new ValidationException("Patient age (" + patient.getAge() + ") is outside valid range [" +
                    MIN_AGE + ", " + MAX_AGE + "]");
        }
        if (patient.getHandicap() < 0 || patient.getHandicap() > MAX_HANDICAP) {
            throw new ValidationException("Handicap score (" + patient.getHandicap() +
                    ") must be between 0 and " + MAX_HANDICAP);
        }
    }

    /**
     * Validates appointment scheduling constraints.
     *
     * @param appointment appointment to validate
     * @throws ValidationException if dates or attributes are inconsistent
     */
    public void validateAppointment(Appointment appointment) {
        if (appointment == null) {
            throw new ValidationException("Appointment reference cannot be null");
        }
        validatePatient(appointment.getPatient());

        LocalDateTime scheduled = appointment.getScheduledDay();
        LocalDate scheduledDate = scheduled.toLocalDate();
        LocalDate appointmentDay = appointment.getAppointmentDay();

        if (appointmentDay.isBefore(scheduledDate)) {
            throw new ValidationException("Appointment date (" + appointmentDay +
                    ") cannot precede scheduling date (" + scheduledDate + ")");
        }

        if (appointment.getNeighbourhood() == null || appointment.getNeighbourhood().trim().isEmpty()) {
            throw new ValidationException("Appointment neighbourhood cannot be blank");
        }
    }

    /**
     * Validates raw values parsed from CSV rows.
     */
    public boolean isValidRecord(int age, LocalDate scheduledDate, LocalDate appointmentDate, int handicap) {
        if (age < MIN_AGE || age > MAX_AGE) return false;
        if (appointmentDate.isBefore(scheduledDate)) return false;
        if (handicap < 0 || handicap > MAX_HANDICAP) return false;
        return true;
    }
}
