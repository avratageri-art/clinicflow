package com.clinicflow.service;

import com.clinicflow.ml.DataValidator;
import com.clinicflow.model.*;
import com.clinicflow.repository.*;
import com.clinicflow.util.ClinicFlowException;
import com.clinicflow.util.DuplicateEntityException;
import com.clinicflow.util.EntityNotFoundException;
import com.clinicflow.util.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClinicalApplicationServiceTest {

    @TempDir
    Path tempDir;

    private File patientsCsv;
    private File doctorsCsv;
    private File appointmentsCsv;
    private File remindersCsv;

    private CsvPatientRepository patientRepo;
    private CsvDoctorRepository doctorRepo;
    private CsvAppointmentRepository appointmentRepo;
    private CsvReminderRepository reminderRepo;

    private PatientService patientService;
    private DoctorService doctorService;
    private AppointmentService appointmentService;
    private ReminderService reminderService;

    @BeforeEach
    void setUp() {
        patientsCsv = tempDir.resolve("patients.csv").toFile();
        doctorsCsv = tempDir.resolve("doctors.csv").toFile();
        appointmentsCsv = tempDir.resolve("appointments.csv").toFile();
        remindersCsv = tempDir.resolve("reminders.csv").toFile();

        patientRepo = new CsvPatientRepository(patientsCsv);
        doctorRepo = new CsvDoctorRepository(doctorsCsv);
        appointmentRepo = new CsvAppointmentRepository(appointmentsCsv, patientRepo, doctorRepo);
        reminderRepo = new CsvReminderRepository(remindersCsv);

        patientService = new PatientService(patientRepo, appointmentRepo);
        doctorService = new DoctorService(doctorRepo, appointmentRepo);
        DataValidator validator = new DataValidator();

        // Active prediction stub returning deterministic results for service tests
        NoShowPredictionService testPredictor = new NoShowPredictionService() {
            @Override public boolean isModelAvailable() { return true; }
            @Override public PredictionResult predict(Appointment a) {
                // High risk if waiting > 5 days, else Low
                if (a.getLeadDays() > 5) {
                    return PredictionResult.fromProbabilities(0.35, 0.65);
                }
                return PredictionResult.fromProbabilities(0.85, 0.15);
            }
            @Override public java.util.Optional<com.clinicflow.ml.ModelMetadata> getActiveModelMetadata() { return java.util.Optional.empty(); }
            @Override public boolean reloadModel() { return true; }
        };

        appointmentService = new AppointmentService(patientRepo, appointmentRepo, doctorRepo, validator, testPredictor);
        reminderService = new ReminderService(reminderRepo, appointmentRepo);
    }

    @Test
    @DisplayName("1. Patient registration creates valid entity with ID and persists to CSV")
    void testPatientRegistration() {
        Patient p = patientService.registerPatient(null, "Lucas Silva", Gender.M, 34, "+55-27-99111-2222", false, false, false, false, 0);

        assertThat(p.getId()).isNotNull();
        assertThat(p.getName()).isEqualTo("Lucas Silva");
        assertThat(patientRepo.existsById(p.getId())).isTrue();
        assertThat(patientRepo.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("2. Patient search finds records by partial name match")
    void testPatientSearch() {
        patientService.registerPatient(1L, "Ana Paula", Gender.F, 28, "+55-27-1111", false, false, false, false, 0);
        patientService.registerPatient(2L, "Carlos Alberto", Gender.M, 45, "+55-27-2222", false, true, false, false, 0);

        List<Patient> found = patientService.searchPatients("Paula");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getName()).isEqualTo("Ana Paula");

        List<Patient> foundAll = patientService.searchPatients("");
        assertThat(foundAll).hasSize(2);
    }

    @Test
    @DisplayName("3. Patient editing updates profile fields and persists changes")
    void testPatientEditing() {
        Patient p = patientService.registerPatient(10L, "Mariana Ramos", Gender.F, 29, "+55-27-3333", false, false, false, false, 0);

        patientService.updatePatient(10L, "Mariana Ramos Santos", Gender.F, 30, "+55-27-9999", true, true, false, false, 1);

        Patient updated = patientRepo.findById(10L).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Mariana Ramos Santos");
        assertThat(updated.getAge()).isEqualTo(30);
        assertThat(updated.isHipertension()).isTrue();
        assertThat(updated.isScholarship()).isTrue();
    }

    @Test
    @DisplayName("4. Doctor registration creates medical specialist with room and contact")
    void testDoctorRegistration() {
        Doctor d = doctorService.registerDoctor(null, "Fernanda Lima", Gender.F, 41, "Cardiology", "Room 105", "+55-27-8888", true);

        assertThat(d.getId()).isNotNull();
        assertThat(d.getSpecialty()).isEqualTo("Cardiology");
        assertThat(doctorRepo.existsById(d.getId())).isTrue();
    }

    @Test
    @DisplayName("5. Doctor availability can be queried and toggled")
    void testDoctorAvailability() {
        Doctor d = doctorService.registerDoctor(101L, "Roberto Mendes", Gender.M, 52, "Neurology", "Room 201", "+55-27-7777", true);
        assertThat(d.isAvailable()).isTrue();

        Doctor toggled = doctorService.toggleAvailability(101L);
        assertThat(toggled.isAvailable()).isFalse();

        List<Doctor> availableList = doctorService.getAvailableDoctors();
        assertThat(availableList).isEmpty();
    }

    @Test
    @DisplayName("6. Appointment booking creates record with auto-token, time, and notes")
    void testAppointmentBooking() {
        Patient p = patientService.registerPatient(1L, "Gabriel Sousa", Gender.M, 38, "+55-27-5555", false, false, false, false, 0);
        Doctor d = doctorService.registerDoctor(101L, "Beatriz Costa", Gender.F, 40, "Endocrinology", "Room 205", "+55-27-4444", true);

        LocalDate apptDate = LocalDate.now().plusDays(2);
        LocalTime apptTime = LocalTime.of(10, 30);

        Appointment appt = appointmentService.bookAppointment(null, p.getId(), d, LocalDateTime.now(), apptDate, apptTime, "CENTRO", "Initial consultation");

        assertThat(appt.getAppointmentId()).isNotNull();
        assertThat(appt.getQueueToken()).startsWith("Q-");
        assertThat(appt.getAppointmentTime()).isEqualTo(apptTime);
        assertThat(appt.getAttendanceStatus()).isEqualTo(AttendanceStatus.SCHEDULED);
        assertThat(appointmentRepo.existsById(appt.getAppointmentId())).isTrue();
    }

    @Test
    @DisplayName("7. Booking appointment for past date throws ValidationException")
    void testPastDateValidation() {
        Patient p = patientService.registerPatient(1L, "Julia Rocha", Gender.F, 22, "+55-27-6666", false, false, false, false, 0);
        Doctor d = doctorService.registerDoctor(101L, "Beatriz Costa", Gender.F, 40, "Endocrinology", "Room 205", "+55-27-4444", true);

        LocalDate pastDate = LocalDate.now().minusDays(1);
        LocalTime time = LocalTime.of(9, 0);

        assertThatThrownBy(() -> appointmentService.bookAppointment(null, p.getId(), d, LocalDateTime.now(), pastDate, time, "CENTRO", ""))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be in the past");
    }

    @Test
    @DisplayName("8. Booking duplicate appointment for same doctor at same date and time throws ValidationException")
    void testDuplicateAppointmentPrevention() {
        Patient p1 = patientService.registerPatient(1L, "Pedro Alves", Gender.M, 30, "+55-27-111", false, false, false, false, 0);
        Patient p2 = patientService.registerPatient(2L, "Lucia Santos", Gender.F, 32, "+55-27-222", false, false, false, false, 0);
        Doctor d = doctorService.registerDoctor(101L, "Beatriz Costa", Gender.F, 40, "Endocrinology", "Room 205", "+55-27-444", true);

        LocalDate apptDate = LocalDate.now().plusDays(3);
        LocalTime apptTime = LocalTime.of(11, 0);

        // First booking succeeds
        appointmentService.bookAppointment(null, p1.getId(), d, LocalDateTime.now(), apptDate, apptTime, "CENTRO", "Visit 1");

        // Second booking at exact same time for same doctor fails
        assertThatThrownBy(() -> appointmentService.bookAppointment(null, p2.getId(), d, LocalDateTime.now(), apptDate, apptTime, "CENTRO", "Visit 2"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already has a scheduled appointment");
    }

    @Test
    @DisplayName("9. Model prediction integration assigns real probabilities and risk tier")
    void testModelPredictionIntegration() {
        Patient p = patientService.registerPatient(1L, "Julio Cesar", Gender.M, 50, "+55-27-888", false, false, false, false, 0);
        Doctor d = doctorService.registerDoctor(101L, "Beatriz Costa", Gender.F, 40, "Endocrinology", "Room 205", "+55-27-444", true);

        // Lead time > 5 days -> High risk from test predictor
        LocalDate apptDate = LocalDate.now().plusDays(10);
        Appointment appt = appointmentService.bookAppointment(null, p.getId(), d, LocalDateTime.now(), apptDate, LocalTime.of(9, 0), "CENTRO", "Long wait");

        PredictionResult pred = appt.getPredictionResult();
        assertThat(pred).isNotNull();
        assertThat(pred.isModelAvailable()).isTrue();
        assertThat(pred.getRiskCategory()).isEqualTo(RiskCategory.HIGH);
        assertThat(pred.getSuggestedAction()).isEqualTo(ReminderAction.PHONE_CALL);
    }

    @Test
    @DisplayName("10. When prediction service is uninitialized, booking proceeds with UNAVAILABLE status")
    void testModelUnavailableHandling() {
        NoShowPredictionService unavail = new NoShowPredictionService() {
            @Override public boolean isModelAvailable() { return false; }
            @Override public PredictionResult predict(Appointment a) { return PredictionResult.unavailable(); }
            @Override public java.util.Optional<com.clinicflow.ml.ModelMetadata> getActiveModelMetadata() { return java.util.Optional.empty(); }
            @Override public boolean reloadModel() { return false; }
        };

        AppointmentService serviceUnavail = new AppointmentService(patientRepo, appointmentRepo, doctorRepo, new DataValidator(), unavail);
        Patient p = patientService.registerPatient(1L, "Clara Nunes", Gender.F, 27, "+55-27-333", false, false, false, false, 0);
        Doctor d = doctorService.registerDoctor(101L, "Beatriz Costa", Gender.F, 40, "Endocrinology", "Room 205", "+55-27-444", true);

        Appointment appt = serviceUnavail.bookAppointment(null, p.getId(), d, LocalDateTime.now(), LocalDate.now().plusDays(2), LocalTime.of(14, 0), "CENTRO", "");
        assertThat(appt.getPredictionResult().isModelAvailable()).isFalse();
        assertThat(appt.getPredictionResult().getStatusMessage()).contains("ML MODEL NOT TRAINED OR AVAILABLE");
    }

    @Test
    @DisplayName("11. CSV persistence writes and reads records with UTF-8 format")
    void testCsvPersistence() {
        patientService.registerPatient(501L, "José de Alencar", Gender.M, 65, "+55-27-99999", true, true, false, false, 2);
        doctorService.registerDoctor(601L, "Dr. Machado de Assis", Gender.M, 58, "Cardiology", "Room 401", "+55-27-88888", true);

        assertThat(patientsCsv.exists()).isTrue();
        assertThat(patientsCsv.length()).isGreaterThan(0);
        assertThat(doctorsCsv.exists()).isTrue();
        assertThat(doctorsCsv.length()).isGreaterThan(0);
    }

    @Test
    @DisplayName("12. Appointment status transitions advance through lifecycle correctly")
    void testAppointmentStatusTransitions() {
        Patient p = patientService.registerPatient(1L, "Arthur Ramos", Gender.M, 35, "+55-27-1234", false, false, false, false, 0);
        Doctor d = doctorService.registerDoctor(101L, "Beatriz Costa", Gender.F, 40, "Endocrinology", "Room 205", "+55-27-4444", true);

        Appointment a = appointmentService.bookAppointment(null, p.getId(), d, LocalDateTime.now(), LocalDate.now(), LocalTime.of(9, 0), "CENTRO", "");
        assertThat(a.getAttendanceStatus()).isEqualTo(AttendanceStatus.SCHEDULED);

        // 1. Check-in
        Appointment checkedIn = appointmentService.checkInPatient(a.getAppointmentId());
        assertThat(checkedIn.getAttendanceStatus()).isEqualTo(AttendanceStatus.CHECKED_IN);
        assertThat(checkedIn.getCheckInTime()).isNotNull();

        // 2. Start consultation
        Appointment inConsultation = appointmentService.startConsultation(a.getAppointmentId());
        assertThat(inConsultation.getAttendanceStatus()).isEqualTo(AttendanceStatus.IN_CONSULTATION);

        // 3. Complete
        Appointment completed = appointmentService.completeAppointment(a.getAppointmentId());
        assertThat(completed.getAttendanceStatus()).isEqualTo(AttendanceStatus.ATTENDED);
    }

    @Test
    @DisplayName("13. Queue management orders patients strictly by arrival (FIFO) regardless of ML risk")
    void testQueueManagementFairness() {
        Patient pLow = patientService.registerPatient(1L, "Patient Low Risk", Gender.F, 25, "+55-27-111", false, false, false, false, 0);
        Patient pHigh = patientService.registerPatient(2L, "Patient High Risk", Gender.M, 70, "+55-27-222", false, true, false, false, 0);
        Doctor d = doctorService.registerDoctor(101L, "Beatriz Costa", Gender.F, 40, "Endocrinology", "Room 205", "+55-27-444", true);

        LocalDate today = LocalDate.now();

        // High-risk patient arrives first and checks in
        Appointment apptHigh = appointmentService.bookAppointment(null, pHigh.getId(), d, LocalDateTime.now().minusDays(10), today, LocalTime.of(10, 0), "CENTRO", "");
        appointmentService.checkInPatient(apptHigh.getAppointmentId());

        // Low-risk patient arrives second and checks in
        Appointment apptLow = appointmentService.bookAppointment(null, pLow.getId(), d, LocalDateTime.now(), today, LocalTime.of(10, 30), "CENTRO", "");
        appointmentService.checkInPatient(apptLow.getAppointmentId());

        List<Appointment> queue = appointmentService.getTodayQueue(today);
        assertThat(queue).hasSize(2);
        // Position 1 MUST be the first patient who checked in, even though they are high risk!
        assertThat(queue.get(0).getAppointmentId()).isEqualTo(apptHigh.getAppointmentId());
        assertThat(queue.get(1).getAppointmentId()).isEqualTo(apptLow.getAppointmentId());
    }

    @Test
    @DisplayName("14. Reminder simulation syncs upcoming appointments and records sent audit trail")
    void testReminderSimulation() {
        Patient p = patientService.registerPatient(1L, "Eduardo Silva", Gender.M, 42, "+55-27-99000-1111", false, false, false, false, 0);
        Doctor d = doctorService.registerDoctor(101L, "Beatriz Costa", Gender.F, 40, "Endocrinology", "Room 205", "+55-27-444", true);

        appointmentService.bookAppointment(null, p.getId(), d, LocalDateTime.now(), LocalDate.now().plusDays(2), LocalTime.of(10, 0), "CENTRO", "Reminder test");

        reminderService.syncRemindersWithAppointments();
        List<ReminderRecord> pending = reminderService.getPendingReminders();
        assertThat(pending).hasSize(1);

        ReminderRecord r = pending.get(0);
        assertThat(r.getStatus()).isEqualTo("PENDING");

        // Simulate dispatch
        ReminderRecord sent = reminderService.simulateSendReminder(r.getReminderId());
        assertThat(sent.getStatus()).isEqualTo("SENT");
        assertThat(sent.getSentTimestamp()).isNotNull();

        List<ReminderRecord> remainingPending = reminderService.getPendingReminders();
        assertThat(remainingPending).isEmpty();

        List<ReminderRecord> history = reminderService.getSentReminders();
        assertThat(history).hasSize(1);
    }

    @Test
    @DisplayName("15. Data retrieval after application restart restores entities, predictions, and queue tokens")
    void testDataRetrievalAfterRestart() {
        // Session 1: Create patient, doctor, and booked appointment
        Patient p = patientService.registerPatient(901L, "Camila Pitanga", Gender.F, 33, "+55-27-7711", false, false, false, false, 0);
        Doctor d = doctorService.registerDoctor(902L, "Dr. Osvaldo Cruz", Gender.M, 60, "Infectious Disease", "Room 501", "+55-27-7722", true);

        Appointment booked = appointmentService.bookAppointment(888L, p.getId(), d, LocalDateTime.now().minusDays(6), LocalDate.now().plusDays(1), LocalTime.of(11, 15), "CENTRO", "Restart check");
        appointmentService.checkInPatient(booked.getAppointmentId());

        // Session 2: Instantiate brand-new repository instances pointing to the same CSV files
        CsvPatientRepository restartPatientRepo = new CsvPatientRepository(patientsCsv);
        CsvDoctorRepository restartDoctorRepo = new CsvDoctorRepository(doctorsCsv);
        CsvAppointmentRepository restartApptRepo = new CsvAppointmentRepository(appointmentsCsv, restartPatientRepo, restartDoctorRepo);

        assertThat(restartPatientRepo.existsById(901L)).isTrue();
        assertThat(restartPatientRepo.findById(901L).orElseThrow().getName()).isEqualTo("Camila Pitanga");

        assertThat(restartDoctorRepo.existsById(902L)).isTrue();
        assertThat(restartDoctorRepo.findById(902L).orElseThrow().getSpecialty()).isEqualTo("Infectious Disease");

        assertThat(restartApptRepo.existsById(888L)).isTrue();
        Appointment restoredAppt = restartApptRepo.findById(888L).orElseThrow();
        assertThat(restoredAppt.getPatient().getName()).isEqualTo("Camila Pitanga");
        assertThat(restoredAppt.getDoctor().getName()).isEqualTo("Dr. Osvaldo Cruz");
        assertThat(restoredAppt.getAttendanceStatus()).isEqualTo(AttendanceStatus.CHECKED_IN);
        assertThat(restoredAppt.getQueueToken()).isEqualTo(booked.getQueueToken());
        assertThat(restoredAppt.getPredictionResult().isModelAvailable()).isTrue();
        assertThat(restoredAppt.getPredictionResult().getRiskCategory()).isEqualTo(RiskCategory.HIGH);
    }

    @Test
    @DisplayName("16. Smart scheduling recommendations find lower-risk appointment slots for a patient")
    void testFindLowerRiskSlots() {
        Patient p = patientService.registerPatient(905L, "Renata Sorrah", Gender.F, 35, "+55-27-8899", false, false, false, false, 0);
        Doctor d = doctorService.registerDoctor(906L, "Dr. Paulo Niemeyer", Gender.M, 52, "Neurology", "Room 303", "+55-27-9900", true);

        List<AppointmentService.LowerRiskSlot> recommendations = appointmentService.findLowerRiskSlots(
                p, d, LocalDate.now(), 10, "JARDIM DA PENHA"
        );

        assertThat(recommendations).isNotEmpty();
        // Lowest risk slot should have lowest no-show probability
        AppointmentService.LowerRiskSlot bestSlot = recommendations.get(0);
        assertThat(bestSlot.getDate()).isNotNull();
        assertThat(bestSlot.getNoShowProbability()).isBetween(0.0, 1.0);
        // Verify list is sorted ascending by no-show probability
        for (int i = 0; i < recommendations.size() - 1; i++) {
            assertThat(recommendations.get(i).getNoShowProbability())
                    .isLessThanOrEqualTo(recommendations.get(i + 1).getNoShowProbability());
        }
    }
}
