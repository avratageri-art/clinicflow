package com.clinicflow.repository;

import com.clinicflow.model.Appointment;
import com.clinicflow.model.AttendanceStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Specialized repository interface for {@link Appointment} records.
 */
public interface AppointmentRepository extends Repository<Appointment, Long> {

    List<Appointment> findByPatientId(Long patientId);

    List<Appointment> findByAppointmentDay(LocalDate date);

    List<Appointment> findByStatus(AttendanceStatus status);
}
