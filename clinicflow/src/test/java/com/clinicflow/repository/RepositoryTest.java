package com.clinicflow.repository;

import com.clinicflow.model.Appointment;
import com.clinicflow.model.AttendanceStatus;
import com.clinicflow.model.Doctor;
import com.clinicflow.model.Gender;
import com.clinicflow.model.Patient;
import com.clinicflow.util.DuplicateEntityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryTest {

    private InMemoryPatientRepository patientRepo;
    private InMemoryAppointmentRepository appointmentRepo;

    @BeforeEach
    void setUp() {
        patientRepo = new InMemoryPatientRepository();
        appointmentRepo = new InMemoryAppointmentRepository();
    }

    @Test
    @DisplayName("Patient repository supports save, find, and count")
    void testPatientRepoBasicOperations() {
        Patient p1 = new Patient(101L, "Alice Smith", Gender.F, 30);
        Patient p2 = new Patient(102L, "Bob Jones", Gender.M, 65, false, true, false, false, 0);

        patientRepo.save(p1);
        patientRepo.save(p2);

        assertThat(patientRepo.count()).isEqualTo(2);
        assertThat(patientRepo.findById(101L)).isPresent().contains(p1);
        assertThat(patientRepo.existsById(102L)).isTrue();
        assertThat(patientRepo.existsById(999L)).isFalse();
    }

    @Test
    @DisplayName("Patient repository prevents duplicate IDs when using saveNew")
    void testDuplicatePatientId() {
        Patient p1 = new Patient(101L, "Alice", Gender.F, 30);
        patientRepo.saveNew(p1);

        Patient duplicate = new Patient(101L, "Alice Second", Gender.F, 31);
        assertThatThrownBy(() -> patientRepo.saveNew(duplicate))
                .isInstanceOf(DuplicateEntityException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Patient repository query by name and chronic conditions")
    void testPatientRepoQueries() {
        Patient p1 = new Patient(101L, "Maria Rodriguez", Gender.F, 45, false, true, false, false, 0);
        Patient p2 = new Patient(102L, "Ana Maria Santos", Gender.F, 22, false, false, false, false, 0);
        Patient p3 = new Patient(103L, "Carlos Silva", Gender.M, 60, false, false, true, false, 0);

        patientRepo.save(p1);
        patientRepo.save(p2);
        patientRepo.save(p3);

        List<Patient> namedMaria = patientRepo.findByNameContaining("maria");
        assertThat(namedMaria).hasSize(2).contains(p1, p2);

        List<Patient> chronic = patientRepo.findByHasChronicConditions();
        assertThat(chronic).hasSize(2).contains(p1, p3);
    }

    @Test
    @DisplayName("Appointment repository query by date and status")
    void testAppointmentRepoQueries() {
        Patient p = new Patient(101L, "Alice", Gender.F, 30);
        Doctor d = new Doctor(1L, "Dr. Luke", Gender.M, 40, "General", "101");
        LocalDateTime now = LocalDateTime.now();
        LocalDate targetDate = LocalDate.of(2026, 6, 1);

        Appointment a1 = new Appointment(201L, p, d, now, targetDate, "CENTRO");
        Appointment a2 = new Appointment(202L, p, d, now, targetDate, "CENTRO");
        Appointment a3 = new Appointment(203L, p, d, now, targetDate.plusDays(1), "CENTRO");

        appointmentRepo.save(a1);
        appointmentRepo.save(a2);
        appointmentRepo.save(a3);

        List<Appointment> forTargetDate = appointmentRepo.findByAppointmentDay(targetDate);
        assertThat(forTargetDate).hasSize(2).contains(a1, a2);

        a1.setAttendanceStatus(AttendanceStatus.ATTENDED);
        appointmentRepo.save(a1);

        List<Appointment> attended = appointmentRepo.findByStatus(AttendanceStatus.ATTENDED);
        assertThat(attended).hasSize(1).contains(a1);
    }
}
