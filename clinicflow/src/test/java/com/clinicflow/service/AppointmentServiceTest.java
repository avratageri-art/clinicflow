package com.clinicflow.service;

import com.clinicflow.ml.DataValidator;
import com.clinicflow.model.Appointment;
import com.clinicflow.model.AttendanceStatus;
import com.clinicflow.model.Doctor;
import com.clinicflow.model.Gender;
import com.clinicflow.model.Patient;
import com.clinicflow.model.PredictionResult;
import com.clinicflow.model.ReminderAction;
import com.clinicflow.model.RiskCategory;
import com.clinicflow.repository.InMemoryAppointmentRepository;
import com.clinicflow.repository.InMemoryPatientRepository;
import com.clinicflow.util.EntityNotFoundException;
import com.clinicflow.util.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentServiceTest {

    private InMemoryPatientRepository patientRepo;
    private InMemoryAppointmentRepository appointmentRepo;
    private DataValidator validator;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        patientRepo = new InMemoryPatientRepository();
        appointmentRepo = new InMemoryAppointmentRepository();
        validator = new DataValidator();
        doctor = new Doctor(1L, "Dr. Gregory House", Gender.M, 50, "Diagnostics", "Clinic 1");
    }

    @Test
    @DisplayName("When ML model is unavailable, service reports ML MODEL NOT TRAINED OR AVAILABLE")
    void testBookAppointmentWhenModelUnavailable() {
        NoShowPredictionService unavailableService = new NoShowPredictionService() {
            @Override
            public boolean isModelAvailable() { return false; }
            @Override
            public PredictionResult predict(Appointment appointment) { return PredictionResult.unavailable(); }
            @Override
            public Optional<com.clinicflow.ml.ModelMetadata> getActiveModelMetadata() { return Optional.empty(); }
            @Override
            public boolean reloadModel() { return false; }
        };

        AppointmentService service = new AppointmentService(patientRepo, appointmentRepo, validator, unavailableService);
        Patient patient = new Patient(101L, "John Doe", Gender.M, 25);
        service.registerPatient(patient);

        LocalDateTime scheduled = LocalDateTime.of(2026, 6, 1, 9, 0);
        LocalDate apptDate = LocalDate.of(2026, 6, 15);

        Appointment appt = service.bookAppointment(201L, 101L, doctor, scheduled, apptDate, "JARDIM DA PENHA");

        assertThat(appt).isNotNull();
        assertThat(appt.getPredictionResult()).isNotNull();
        assertThat(appt.getPredictionResult().isModelAvailable()).isFalse();
        assertThat(appt.getPredictionResult().getStatusMessage()).contains("ML MODEL NOT TRAINED OR AVAILABLE");
        assertThat(appt.getPredictionResult().getPredictedClass()).isEqualTo("UNAVAILABLE");
    }

    @Test
    @DisplayName("When ML model is available, service attaches genuine prediction result")
    void testBookAppointmentWithActiveModel() {
        NoShowPredictionService activeService = new NoShowPredictionService() {
            @Override
            public boolean isModelAvailable() { return true; }
            @Override
            public PredictionResult predict(Appointment appointment) {
                // Genuine prediction stub returning calculated probabilities
                return PredictionResult.fromProbabilities(0.72, 0.28);
            }
            @Override
            public Optional<com.clinicflow.ml.ModelMetadata> getActiveModelMetadata() { return Optional.empty(); }
            @Override
            public boolean reloadModel() { return true; }
        };

        AppointmentService service = new AppointmentService(patientRepo, appointmentRepo, validator, activeService);
        Patient patient = new Patient(101L, "John Doe", Gender.M, 25);
        service.registerPatient(patient);

        LocalDateTime scheduled = LocalDateTime.of(2026, 6, 1, 9, 0);
        LocalDate apptDate = LocalDate.of(2026, 6, 15);

        Appointment appt = service.bookAppointment(201L, 101L, doctor, scheduled, apptDate, "JARDIM DA PENHA");

        assertThat(appt).isNotNull();
        assertThat(appt.getPredictionResult().isModelAvailable()).isTrue();
        assertThat(appt.getPredictionResult().getNoShowProbability()).isEqualTo(0.28);
        assertThat(appt.getPredictionResult().getAttendanceProbability()).isEqualTo(0.72);
        assertThat(appt.getPredictionResult().getRiskCategory()).isEqualTo(RiskCategory.MEDIUM);
        assertThat(appt.getPredictionResult().getSuggestedAction()).isEqualTo(ReminderAction.ENHANCED_SMS);
    }

    @Test
    @DisplayName("Booking appointment for unregistered patient throws EntityNotFoundException")
    void testBookAppointmentUnknownPatient() {
        AppointmentService service = new AppointmentService(patientRepo, appointmentRepo, validator);
        LocalDateTime scheduled = LocalDateTime.of(2026, 6, 1, 9, 0);
        LocalDate apptDate = LocalDate.of(2026, 6, 15);

        assertThatThrownBy(() -> service.bookAppointment(202L, 9999L, doctor, scheduled, apptDate, "CENTRO"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Patient with ID 9999 not found");
    }

    @Test
    @DisplayName("Booking appointment with invalid date throws ValidationException")
    void testBookAppointmentInvalidDate() {
        AppointmentService service = new AppointmentService(patientRepo, appointmentRepo, validator);
        Patient patient = new Patient(102L, "Jane Doe", Gender.F, 35);
        service.registerPatient(patient);

        LocalDateTime scheduled = LocalDateTime.of(2026, 6, 15, 9, 0);
        LocalDate invalidApptDate = LocalDate.of(2026, 6, 1); // Before booking date!

        assertThatThrownBy(() -> service.bookAppointment(203L, 102L, doctor, scheduled, invalidApptDate, "CENTRO"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot precede scheduling date");
    }

    @Test
    @DisplayName("Recording attendance outcome updates appointment state")
    void testRecordAttendanceOutcome() {
        AppointmentService service = new AppointmentService(patientRepo, appointmentRepo, validator);
        Patient patient = new Patient(103L, "Bob Ross", Gender.M, 52);
        service.registerPatient(patient);

        LocalDateTime scheduled = LocalDateTime.of(2026, 6, 1, 9, 0);
        LocalDate apptDate = LocalDate.of(2026, 6, 2);

        Appointment appt = service.bookAppointment(204L, 103L, doctor, scheduled, apptDate, "CENTRO");
        assertThat(appt.getAttendanceStatus()).isEqualTo(AttendanceStatus.SCHEDULED);

        service.recordAttendanceOutcome(204L, AttendanceStatus.ATTENDED);
        Appointment updated = appointmentRepo.findById(204L).orElseThrow();
        assertThat(updated.getAttendanceStatus()).isEqualTo(AttendanceStatus.ATTENDED);
    }
}
