package com.clinicflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelTest {

    @Test
    @DisplayName("Patient creation with valid attributes succeeds")
    void testPatientCreationValid() {
        Patient p = new Patient(1001L, "Carlos Gomez", Gender.M, 45, true, true, false, false, 0);

        assertThat(p.getId()).isEqualTo(1001L);
        assertThat(p.getName()).isEqualTo("Carlos Gomez");
        assertThat(p.getGender()).isEqualTo(Gender.M);
        assertThat(p.getAge()).isEqualTo(45);
        assertThat(p.isScholarship()).isTrue();
        assertThat(p.isHipertension()).isTrue();
        assertThat(p.isDiabetes()).isFalse();
        assertThat(p.hasChronicConditions()).isTrue();
    }

    @Test
    @DisplayName("Patient creation with invalid age throws IllegalArgumentException")
    void testPatientInvalidAge() {
        assertThatThrownBy(() -> new Patient(1002L, "Invalid Age", Gender.F, -5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Age must be between 0 and 125");
    }

    @Test
    @DisplayName("Patient creation with invalid handicap throws IllegalArgumentException")
    void testPatientInvalidHandicap() {
        assertThatThrownBy(() -> new Patient(1003L, "Invalid Handicap", Gender.M, 30, false, false, false, false, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Handicap score must be between 0 and 4");
    }

    @Test
    @DisplayName("Doctor inheritance and polymorphism work correctly")
    void testDoctorInheritance() {
        Doctor doc = new Doctor(2001L, "Elena Rostova", Gender.F, 39, "Cardiology", "Room 402");
        Person person = doc; // Polymorphism

        assertThat(person.getName()).isEqualTo("Elena Rostova");
        assertThat(person.getRoleDescription()).contains("Elena Rostova").contains("Cardiology");
    }

    @Test
    @DisplayName("Appointment lead days calculation works accurately")
    void testAppointmentLeadDays() {
        Patient patient = new Patient(3001L, "Ana Silva", Gender.F, 28);
        Doctor doctor = new Doctor(2001L, "Dr. Elena", Gender.F, 39, "Cardiology", "402");

        LocalDateTime scheduled = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDate apptDate = LocalDate.of(2026, 5, 15);

        Appointment appt = new Appointment(5001L, patient, doctor, scheduled, apptDate, "JARDIM DA PENHA");

        assertThat(appt.getLeadDays()).isEqualTo(14);
        assertThat(appt.getScheduledDayOfWeek()).isEqualTo("FRIDAY");
        assertThat(appt.getAppointmentDayOfWeek()).isEqualTo("FRIDAY");
    }

    @Test
    @DisplayName("RiskCategory mapping thresholds work correctly")
    void testRiskCategoryThresholds() {
        assertThat(RiskCategory.fromProbability(0.05)).isEqualTo(RiskCategory.LOW);
        assertThat(RiskCategory.fromProbability(0.19)).isEqualTo(RiskCategory.LOW);
        assertThat(RiskCategory.fromProbability(0.20)).isEqualTo(RiskCategory.MEDIUM);
        assertThat(RiskCategory.fromProbability(0.49)).isEqualTo(RiskCategory.MEDIUM);
        assertThat(RiskCategory.fromProbability(0.50)).isEqualTo(RiskCategory.HIGH);
        assertThat(RiskCategory.fromProbability(0.85)).isEqualTo(RiskCategory.HIGH);
    }

    @Test
    @DisplayName("ReminderAction mapping reflects risk category appropriately")
    void testReminderActions() {
        assertThat(ReminderAction.forRisk(RiskCategory.LOW)).isEqualTo(ReminderAction.STANDARD);
        assertThat(ReminderAction.forRisk(RiskCategory.MEDIUM)).isEqualTo(ReminderAction.ENHANCED_SMS);
        assertThat(ReminderAction.forRisk(RiskCategory.HIGH)).isEqualTo(ReminderAction.PHONE_CALL);
    }
}
