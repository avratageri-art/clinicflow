package com.clinicflow.web;

import com.clinicflow.model.*;
import com.clinicflow.service.AppointmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseMapperTest {

    @Test
    @DisplayName("Maps Patient, Doctor, Appointment, ReminderRecord, PredictionResult, and LowerRiskSlot cleanly")
    void testDomainMappings() {
        Patient p = new Patient(1001L, "Maria Santos", Gender.F, 56, false, true, false, false, 0, "+55-27-99111-0001");
        Doctor d = new Doctor(201L, "Dr. Beatriz Silva", Gender.F, 42, "Cardiology", "Room 201", "+55-27-98111-0001", true);
        Appointment a = new Appointment(5001L, p, d, LocalDateTime.now(), LocalDate.now().plusDays(12),
                LocalTime.of(10, 30), "Q-007", "CENTRO", "");
        PredictionResult pred = new PredictionResult("YES", 0.62, 0.38, RiskCategory.HIGH, ReminderAction.PHONE_CALL);
        a.setPredictionResult(pred);
        ReminderRecord rem = ReminderRecord.fromAppointment(101L, a);

        Map<String, Object> patMap = ApiResponseMapper.toPatientMap(p);
        assertThat(patMap.get("id")).isEqualTo(1001L);
        assertThat(patMap.get("name")).isEqualTo("Maria Santos");

        Map<String, Object> docMap = ApiResponseMapper.toDoctorMap(d);
        assertThat(docMap.get("specialty")).isEqualTo("Cardiology");

        Map<String, Object> apptMap = ApiResponseMapper.toAppointmentMap(a);
        assertThat(apptMap.get("queueToken")).isEqualTo("Q-007");

        Map<String, Object> predMap = ApiResponseMapper.toPredictionMap(pred);
        assertThat(predMap.get("suggestedAction")).isEqualTo("PHONE_CALL");
        assertThat(predMap.get("recommendedAction")).isEqualTo("PHONE_CALL");

        Map<String, Object> remMap = ApiResponseMapper.toReminderMap(rem);
        assertThat(remMap.get("riskCategory")).isEqualTo("HIGH");

        AppointmentService.LowerRiskSlot slot = new AppointmentService.LowerRiskSlot(
                LocalDate.now().plusDays(1), LocalTime.of(9, 30), 1,
                new PredictionResult("NO", 0.10, 0.90, RiskCategory.LOW, ReminderAction.STANDARD));
        assertThat(ApiResponseMapper.toSlotMap(slot).get("riskCategory")).isEqualTo("LOW");
    }

    @Test
    @DisplayName("buildTriageGuidance adapts to HIGH, MEDIUM, LOW, and unavailable predictions")
    void testTriageGuidance() {
        assertThat(ApiResponseMapper.buildTriageGuidance(null, 0, 0)).contains("unavailable");
        assertThat(ApiResponseMapper.buildTriageGuidance(
                new PredictionResult("YES", 0.75, 0.25, RiskCategory.HIGH, ReminderAction.PHONE_CALL), 10, 12))
                .contains("phone call", "High today queue load");
        assertThat(ApiResponseMapper.buildTriageGuidance(
                new PredictionResult("NO", 0.35, 0.65, RiskCategory.MEDIUM, ReminderAction.ENHANCED_SMS), 3, 2))
                .contains("SMS confirmation link");
        assertThat(ApiResponseMapper.buildTriageGuidance(
                new PredictionResult("NO", 0.05, 0.95, RiskCategory.LOW, ReminderAction.STANDARD), 0, 1))
                .contains("Same-day booking", "Standard automated 24h SMS");
    }
}
