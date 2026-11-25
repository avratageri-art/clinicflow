package com.clinicflow;

import com.clinicflow.ml.DataPreprocessor;
import com.clinicflow.ml.FeatureExtractor;
import com.clinicflow.ml.ModelMetadata;
import com.clinicflow.ml.ModelRepository;
import com.clinicflow.model.*;
import com.clinicflow.repository.*;
import com.clinicflow.service.*;
import com.clinicflow.util.ApplicationConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import weka.classifiers.trees.RandomForest;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EndToEndWorkflowTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Verify complete 17-step clinical workflow end-to-end with persistent storage and temporary Weka test model")
    void testCompleteClinicFlowWorkflow() throws Exception {
        System.out.println("=== Starting End-to-End ClinicFlow Workflow Test ===");

        ApplicationConfig config = ApplicationConfig.getInstance();

        File patientsCsv = tempDir.resolve("patients.csv").toFile();
        File doctorsCsv = tempDir.resolve("doctors.csv").toFile();
        File appointmentsCsv = tempDir.resolve("appointments.csv").toFile();
        File remindersCsv = tempDir.resolve("reminders.csv").toFile();

        // Build a temporary Weka RandomForest test model from synthetic sample_appointments.csv
        File sampleRaw = config.getProjectRoot().resolve("data").resolve("raw").resolve("sample_appointments.csv").toFile();
        File tempClean = tempDir.resolve("clean_sample.csv").toFile();
        File tempReport = tempDir.resolve("clean_report.md").toFile();
        DataPreprocessor preprocessor = new DataPreprocessor();
        preprocessor.cleanDataset(sampleRaw, tempClean, tempReport);
        DataPreprocessor.PreprocessedData split = preprocessor.splitChronologically(tempClean, 0.80);

        RandomForest testRf = new RandomForest();
        testRf.setNumIterations(10);
        testRf.setSeed(42);
        testRf.buildClassifier(split.getTrainInstances());

        File tempModelFile = tempDir.resolve("test-rf.model").toFile();
        File tempMetaFile = tempDir.resolve("test-meta.ser").toFile();
        ModelRepository modelRepo = new ModelRepository();
        modelRepo.saveModel(testRf, tempModelFile);
        modelRepo.saveMetadata(new ModelMetadata(
                "test-1.0", "Weka RandomForest (Test Fixture)", "2026-11-25",
                200, split.getTrainCount(), split.getTestCount(),
                new FeatureExtractor().getFeatureNames(), "noShow", List.of("NO", "YES"),
                10, 0, 42, 0.80, 0.70, 0.60, 0.65, 0.75
        ), tempMetaFile);

        // Verify graceful no-model path before loading the temporary test model
        WekaNoShowPredictionService unavailableService = new WekaNoShowPredictionService(
                modelRepo, tempDir.resolve("missing.model").toFile(), tempDir.resolve("missing.ser").toFile()
        );
        assertThat(unavailableService.isModelAvailable()).isFalse();

        // Step 1: Initialize Persistent Repositories and Weka ML Service using temporary test model
        PatientRepository patientRepo = new CsvPatientRepository(patientsCsv);
        DoctorRepository doctorRepo = new CsvDoctorRepository(doctorsCsv);
        AppointmentRepository apptRepo = new CsvAppointmentRepository(appointmentsCsv, patientRepo, doctorRepo);
        ReminderRepository reminderRepo = new CsvReminderRepository(remindersCsv);

        WekaNoShowPredictionService mlService = new WekaNoShowPredictionService(modelRepo, tempModelFile, tempMetaFile);
        assertThat(mlService.isModelAvailable()).isTrue();
        System.out.println("[Step 1 & 2] Application & Temporary Weka Test Model Initialized Successfully.");

        PatientService patientService = new PatientService(patientRepo, apptRepo);
        DoctorService doctorService = new DoctorService(doctorRepo, apptRepo);
        AppointmentService appointmentService = new AppointmentService(patientRepo, apptRepo, doctorRepo, new com.clinicflow.ml.DataValidator(), mlService);
        ReminderService reminderService = new ReminderService(reminderRepo, apptRepo);
        ModelInsightsService insightsService = new ModelInsightsService(config);

        // Step 3: Register a new patient
        Patient pat = patientService.registerPatient(
                99001L, "Eduardo Vergueiro", Gender.M, 46, "+55-27-99888-7777",
                false, true, false, false, 0
        );
        assertThat(pat).isNotNull();
        assertThat(patientRepo.existsById(99001L)).isTrue();
        System.out.println("[Step 3] Patient Registered: " + pat.getName() + " (ID: " + pat.getId() + ")");

        // Step 4: Register a new doctor
        Doctor doc = doctorService.registerDoctor(
                88001L, "Helena Rubinstein", Gender.F, 39, "Dermatology", "Room 405", "+55-27-99777-6666", true
        );
        assertThat(doc).isNotNull();
        assertThat(doctorRepo.existsById(88001L)).isTrue();
        System.out.println("[Step 4] Doctor Registered: Dr. " + doc.getName() + " (" + doc.getSpecialty() + ")");

        // Step 5, 6, 7 & 8: Book a new appointment & generate genuine ML prediction and queue token
        LocalDate apptDate = LocalDate.now().plusDays(4);
        LocalTime apptTime = LocalTime.of(14, 30);
        Appointment appt = appointmentService.bookAppointment(
                77001L, pat.getId(), doc, LocalDateTime.now(), apptDate, apptTime, "JARDIM DA PENHA", "Follow-up rash examination"
        );

        assertThat(appt).isNotNull();
        assertThat(appt.getQueueToken()).isNotBlank();
        assertThat(appt.getPredictionResult()).isNotNull();
        assertThat(appt.getPredictionResult().isModelAvailable()).isTrue();
        assertThat(appt.getPredictionResult().getPredictedClass()).isIn("YES", "NO");
        assertThat(appt.getPredictionResult().getNoShowProbability()).isBetween(0.0, 1.0);
        System.out.printf("[Step 5-8] Appointment Booked #%d | Token: %s | ML No-Show Prob: %.2f%% | Risk: %s | Action: %s\n",
                appt.getAppointmentId(), appt.getQueueToken(),
                appt.getPredictionResult().getNoShowProbability() * 100.0,
                appt.getPredictionResult().getRiskCategory(),
                appt.getPredictionResult().getSuggestedAction());

        // Step 9 & 10: Check in the patient and view in today's queue
        // For queue verification, let's create a today's appointment and check in
        Appointment todayAppt = appointmentService.bookAppointment(
                77002L, pat.getId(), doc, LocalDateTime.now(), LocalDate.now(), LocalTime.of(16, 0), "CENTRO", "Today consultation"
        );
        appointmentService.checkInPatient(todayAppt.getAppointmentId());
        List<Appointment> queue = appointmentService.getTodayQueue(LocalDate.now());
        assertThat(queue).anyMatch(a -> a.getAppointmentId().equals(77002L) && a.getAttendanceStatus() == AttendanceStatus.CHECKED_IN);
        System.out.println("[Step 9 & 10] Patient Checked In for Today. Token in Queue: " + todayAppt.getQueueToken());

        // Step 11: Advance consultation and mark completed
        appointmentService.startConsultation(todayAppt.getAppointmentId());
        appointmentService.completeAppointment(todayAppt.getAppointmentId());
        Appointment completed = appointmentService.getAppointmentById(77002L).orElseThrow();
        assertThat(completed.getAttendanceStatus()).isEqualTo(AttendanceStatus.ATTENDED);
        System.out.println("[Step 11] Consultation Completed. Outcome: " + completed.getAttendanceStatus());

        // Step 12: Simulate a reminder for upcoming appointment
        reminderService.syncRemindersWithAppointments();
        List<ReminderRecord> pending = reminderService.getPendingReminders();
        assertThat(pending).isNotEmpty();
        ReminderRecord targetRem = pending.stream()
                .filter(r -> r.getAppointmentId().equals(appt.getAppointmentId()))
                .findFirst().orElseThrow();

        ReminderRecord sentRem = reminderService.simulateSendReminder(targetRem.getReminderId());
        assertThat(sentRem.getStatus()).isEqualTo("SENT");
        assertThat(sentRem.getSentTimestamp()).isNotNull();
        System.out.println("[Step 12] Reminder Simulated Sent: " + sentRem.getRecommendedAction() + " at " + sentRem.getSentTimestamp());

        // Step 13: View appointment history for patient
        List<Appointment> history = patientService.getPatientHistory(pat.getId());
        assertThat(history).hasSizeGreaterThanOrEqualTo(2);
        System.out.println("[Step 13] Patient Appointment History Retrieved (" + history.size() + " records).");

        // Step 14 & 15: Open ML Model Insights and view evaluation metrics
        ModelInsightsService.ModelSpec spec = insightsService.getModelSpecification();
        assertThat(spec.accuracy).isGreaterThan(0.70);
        assertThat(spec.f1).isGreaterThan(0.20);
        assertThat(spec.features).contains("leadTimeDays", "age");
        System.out.println("[Step 14 & 15] Model Insights Verified: Algorithm=" + spec.algorithm + " | Accuracy=" + String.format("%.2f%%", spec.accuracy * 100));

        // Step 16 & 17: Application restart simulation — instantiate new repositories and verify persistence
        CsvPatientRepository restartPatRepo = new CsvPatientRepository(patientsCsv);
        CsvDoctorRepository restartDocRepo = new CsvDoctorRepository(doctorsCsv);
        CsvAppointmentRepository restartApptRepo = new CsvAppointmentRepository(appointmentsCsv, restartPatRepo, restartDocRepo);
        CsvReminderRepository restartRemRepo = new CsvReminderRepository(remindersCsv);

        assertThat(restartPatRepo.existsById(99001L)).isTrue();
        assertThat(restartDocRepo.existsById(88001L)).isTrue();
        assertThat(restartApptRepo.existsById(77001L)).isTrue();
        assertThat(restartApptRepo.findById(77001L).orElseThrow().getQueueToken()).isEqualTo(appt.getQueueToken());
        assertThat(restartRemRepo.findByAppointmentId(77001L)).isNotEmpty();
        System.out.println("[Step 16 & 17] Application Restart Verification PASSED: All CSV records, predictions & tokens persisted!");

        System.out.println("=== All 17 End-to-End Workflow Steps Successfully Verified! ===");
    }
}
