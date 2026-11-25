package com.clinicflow.service;

import com.clinicflow.ml.DataValidator;
import com.clinicflow.model.*;
import com.clinicflow.repository.*;
import com.clinicflow.util.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClinicalAssistantServiceTest {

    private PatientService patientService;
    private DoctorService doctorService;
    private AppointmentService appointmentService;
    private ReminderService reminderService;
    private NoShowPredictionService stubPrediction;
    private ClinicalAssistantService assistant;

    @BeforeEach
    void setUp() {
        PatientRepository patRepo = new InMemoryPatientRepository();
        DoctorRepository docRepo = new InMemoryDoctorRepository();
        AppointmentRepository apptRepo = new InMemoryAppointmentRepository();
        ReminderRepository remRepo = new InMemoryReminderRepository();

        stubPrediction = new NoShowPredictionService() {
            @Override
            public boolean isModelAvailable() { return true; }

            @Override
            public PredictionResult predict(Appointment appt) {
                double prob = appt.getLeadDays() >= 10 ? 0.65 : 0.12;
                RiskCategory risk = RiskCategory.fromProbability(prob);
                ReminderAction action = risk == RiskCategory.HIGH ? ReminderAction.PHONE_CALL : ReminderAction.STANDARD;
                return new PredictionResult(prob >= 0.5 ? "YES" : "NO", prob, 1.0 - prob, risk, action);
            }

            @Override
            public Optional<com.clinicflow.ml.ModelMetadata> getActiveModelMetadata() { return Optional.empty(); }

            @Override
            public boolean reloadModel() { return true; }
        };

        patientService = new PatientService(patRepo, apptRepo);
        doctorService = new DoctorService(docRepo, apptRepo);
        appointmentService = new AppointmentService(patRepo, apptRepo, docRepo, new DataValidator(), stubPrediction);
        reminderService = new ReminderService(remRepo, apptRepo);
        assistant = new ClinicalAssistantService(patientService, doctorService, appointmentService, reminderService, stubPrediction);

        Patient p1 = patientService.registerPatient(1001L, "Maria Santos", Gender.F, 56, "+55-27-99111-0001", false, true, false, false, 0);
        Doctor d1 = doctorService.registerDoctor(201L, "Dr. Beatriz Silva", Gender.F, 42, "Cardiology", "Room 201", "+55-27-98111-0001", true);
        doctorService.registerDoctor(202L, "Dr. Ricardo Mendes", Gender.M, 48, "General", "Room 105", "+55-27-98111-0002", true);
        appointmentService.bookAppointment(5001L, p1.getId(), d1, LocalDateTime.now(), LocalDate.now().plusDays(14), LocalTime.of(10, 0), "CENTRO", "");
    }

    @Test
    @DisplayName("Constructor enforces non-null service preconditions")
    void testConstructorNullGuards() {
        assertThatThrownBy(() -> new ClinicalAssistantService(null, doctorService, appointmentService, reminderService, stubPrediction))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ClinicalAssistantService(patientService, null, appointmentService, reminderService, stubPrediction))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ClinicalAssistantService(patientService, doctorService, null, reminderService, stubPrediction))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Blank or null query throws ValidationException")
    void testEmptyQueryRejected() {
        assertThatThrownBy(() -> assistant.answerQuery("   ", null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> assistant.answerQuery(null, null)).isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("CLINIC_BRIEFING surfaces contextSignals and unreminded high-risk bottleneck")
    void testClinicBriefingAndContextSignals() {
        Map<String, Object> res = assistant.answerQuery("Give me today's clinic briefing", null);
        assertThat(res.get("intent")).isEqualTo("CLINIC_BRIEFING");
        @SuppressWarnings("unchecked")
        Map<String, Object> signals = (Map<String, Object>) res.get("contextSignals");
        assertThat(signals.get("totalAppointments")).isEqualTo(1);
        assertThat(signals.get("highRiskCount")).isEqualTo(1L);
        assertThat(signals.get("unremindedHighRiskCount")).isEqualTo(1L);
        assertThat(signals.get("busiestDoctor")).isEqualTo("Dr. Beatriz Silva");
    }

    @Test
    @DisplayName("HIGH_RISK_TRIAGE ranks high-risk appointments and attaches SEND_REMINDER action")
    void testHighRiskTriageIntent() {
        Map<String, Object> res = assistant.answerQuery("Who are my highest-risk patients?", null);
        assertThat(res.get("intent")).isEqualTo("HIGH_RISK_TRIAGE");
        assertThat(res.get("answer").toString()).contains("Maria Santos");
    }

    @Test
    @DisplayName("DOCTOR_LOAD_BALANCING and DOCTOR_CONTEXT intents evaluate physician load")
    void testDoctorIntents() {
        Map<String, Object> load = assistant.answerQuery("Which doctors are overloaded?", null);
        assertThat(load.get("intent")).isEqualTo("DOCTOR_LOAD_BALANCING");

        Map<String, Object> docCtx = assistant.answerQuery("Check Dr. Beatriz Silva", null);
        assertThat(docCtx.get("intent")).isEqualTo("DOCTOR_CONTEXT");
        assertThat(docCtx.get("answer").toString()).contains("1 active appointment");
    }

    @Test
    @DisplayName("REMINDER_ACTION_PLAN and OPTIMAL_SLOT_FINDER intents return executable actions")
    void testReminderAndSlotIntents() {
        Map<String, Object> rem = assistant.answerQuery("Generate reminder action plan", null);
        assertThat(rem.get("intent")).isEqualTo("REMINDER_ACTION_PLAN");

        Map<String, Object> slots = assistant.answerQuery("Suggest optimal low-risk slots", 1001L);
        assertThat(slots.get("intent")).isEqualTo("OPTIMAL_SLOT_FINDER");
    }

    @Test
    @DisplayName("PATIENT_CONTEXT handles known patient and falls back gracefully on unknown ID")
    void testPatientContextAndUnknownFallback() {
        Map<String, Object> known = assistant.answerQuery("Assess patient 1001", null);
        assertThat(known.get("intent")).isEqualTo("PATIENT_CONTEXT");
        assertThat(known.get("answer").toString()).contains("Maria Santos");

        Map<String, Object> unknown = assistant.answerQuery("Assess patient 999999", null);
        assertThat(unknown.get("intent")).isEqualTo("CLINIC_BRIEFING");
    }
}
