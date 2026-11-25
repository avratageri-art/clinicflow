package com.clinicflow.service;

import com.clinicflow.ml.DataValidator;
import com.clinicflow.model.*;
import com.clinicflow.repository.AppointmentRepository;
import com.clinicflow.repository.CsvAppointmentRepository;
import com.clinicflow.repository.DoctorRepository;
import com.clinicflow.repository.PatientRepository;
import com.clinicflow.util.ClinicFlowException;
import com.clinicflow.util.EntityNotFoundException;
import com.clinicflow.util.ValidationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core clinical coordinator service managing appointment bookings,
 * doctor scheduling validations, queue state transitions, and real-time Weka ML predictions.
 */
public class AppointmentService {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final DataValidator dataValidator;
    private final NoShowPredictionService predictionService;

    public AppointmentService(PatientRepository patientRepository,
                              AppointmentRepository appointmentRepository,
                              DoctorRepository doctorRepository,
                              DataValidator dataValidator,
                              NoShowPredictionService predictionService) {
        this.patientRepository = Objects.requireNonNull(patientRepository, "patientRepository cannot be null");
        this.appointmentRepository = Objects.requireNonNull(appointmentRepository, "appointmentRepository cannot be null");
        this.doctorRepository = doctorRepository;
        this.dataValidator = Objects.requireNonNull(dataValidator, "dataValidator cannot be null");
        this.predictionService = Objects.requireNonNull(predictionService, "predictionService cannot be null");
    }

    public AppointmentService(PatientRepository patientRepository,
                              AppointmentRepository appointmentRepository,
                              DataValidator dataValidator,
                              NoShowPredictionService predictionService) {
        this(patientRepository, appointmentRepository, null, dataValidator, predictionService);
    }

    public AppointmentService(PatientRepository patientRepository,
                              AppointmentRepository appointmentRepository,
                              DataValidator dataValidator) {
        this(patientRepository, appointmentRepository, null, dataValidator, new WekaNoShowPredictionService());
    }

    /**
     * Registers a new patient into the clinic system.
     */
    public Patient registerPatient(Patient patient) {
        dataValidator.validatePatient(patient);
        return patientRepository.save(patient);
    }

    /**
     * Legacy booking method for backward compatibility with Stage 1 and Stage 2 test suites.
     */
    public Appointment bookAppointment(Long appointmentId, Long patientId, Doctor doctor,
                                       LocalDateTime scheduledDay, LocalDate appointmentDay,
                                       String neighbourhood) {
        return bookAppointment(appointmentId, patientId, doctor, scheduledDay, appointmentDay,
                LocalTime.of(9, 0), neighbourhood, "");
    }

    /**
     * Comprehensive clinical booking method with time, notes, doctor availability check,
     * duplicate slot prevention, past-date validation, and automatic queue token assignment.
     */
    public Appointment bookAppointment(Long appointmentId, Long patientId, Doctor doctor,
                                       LocalDateTime scheduledDay, LocalDate appointmentDay,
                                       LocalTime appointmentTime, String neighbourhood, String notes) {
        if (patientId == null) {
            throw new ValidationException("Patient ID is required for booking");
        }
        if (doctor == null) {
            throw new ValidationException("Attending doctor is required for booking");
        }
        if (appointmentDay == null) {
            throw new ValidationException("Appointment date cannot be null");
        }

        // 1. Validation: Prevent booking in the past
        LocalDate bookingDate = scheduledDay != null ? scheduledDay.toLocalDate() : LocalDate.now();
        if (appointmentDay.isBefore(bookingDate)) {
            throw new ValidationException("Appointment date (" + appointmentDay +
                    ") cannot be in the past (cannot precede scheduling date " + bookingDate + ")");
        }

        // 2. Validation: Doctor availability
        if (!doctor.isAvailable()) {
            throw new ValidationException("Doctor " + doctor.getName() + " is currently unavailable for consultations");
        }

        // 3. Validation: Verify Patient exists
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new EntityNotFoundException("Patient with ID " + patientId + " not found"));

        LocalDateTime sched = scheduledDay != null ? scheduledDay : LocalDateTime.now();
        LocalTime apptTime = appointmentTime != null ? appointmentTime : LocalTime.of(9, 0);

        // 4. Validation: Prevent duplicate appointment for same doctor at same date and time
        boolean duplicate = appointmentRepository.findAll().stream()
                .filter(a -> a.getDoctor() != null && a.getDoctor().getId().equals(doctor.getId()))
                .filter(a -> a.getAppointmentDay().equals(appointmentDay))
                .filter(a -> a.getAppointmentTime() != null &&
                        a.getAppointmentTime().getHour() == apptTime.getHour() &&
                        a.getAppointmentTime().getMinute() == apptTime.getMinute())
                .anyMatch(a -> a.getAttendanceStatus() != AttendanceStatus.CANCELLED);

        if (duplicate) {
            throw new ValidationException(String.format(
                    "Doctor %s already has a scheduled appointment on %s at %02d:%02d",
                    doctor.getName(), appointmentDay, apptTime.getHour(), apptTime.getMinute()));
        }

        // 5. Generate ID and Queue Token if needed
        Long apptId = appointmentId;
        if (apptId == null) {
            if (appointmentRepository instanceof CsvAppointmentRepository) {
                apptId = ((CsvAppointmentRepository) appointmentRepository).generateNextId();
            } else {
                apptId = System.currentTimeMillis();
            }
        }

        // Token generation format: Q-XXX
        long tokenNum = (Math.abs(apptId) % 1000) + 1;
        String token = "Q-" + String.format("%03d", tokenNum);

        String hood = (neighbourhood != null && !neighbourhood.trim().isEmpty()) ?
                neighbourhood.trim() : "CENTRO";

        Appointment appointment = new Appointment(
                apptId, patient, doctor, sched, appointmentDay, apptTime,
                token, hood, notes
        );
        dataValidator.validateAppointment(appointment);

        // 6. Execute genuine ML inference via prediction service
        PredictionResult prediction = predictionService.predict(appointment);
        appointment.setPredictionResult(prediction);

        return appointmentRepository.save(appointment);
    }

    /**
     * Checks in a patient at the clinic reception, placing them into today's queue.
     */
    public Appointment checkInPatient(Long appointmentId) {
        Appointment appt = findAppointmentOrThrow(appointmentId);
        if (appt.getAttendanceStatus().isFinished()) {
            throw new ClinicFlowException("Cannot check in appointment that is already " + appt.getAttendanceStatus());
        }
        appt.setAttendanceStatus(AttendanceStatus.CHECKED_IN);
        appt.setCheckInTime(LocalDateTime.now());
        return appointmentRepository.save(appt);
    }

    /**
     * Advances patient consultation to 'IN_CONSULTATION'.
     */
    public Appointment startConsultation(Long appointmentId) {
        Appointment appt = findAppointmentOrThrow(appointmentId);
        if (appt.getAttendanceStatus() != AttendanceStatus.CHECKED_IN) {
            throw new ClinicFlowException("Patient must be checked-in before starting consultation (Current: " +
                    appt.getAttendanceStatus() + ")");
        }
        appt.setAttendanceStatus(AttendanceStatus.IN_CONSULTATION);
        return appointmentRepository.save(appt);
    }

    /**
     * Marks consultation as successfully completed / attended.
     */
    public Appointment completeAppointment(Long appointmentId) {
        Appointment appt = findAppointmentOrThrow(appointmentId);
        appt.setAttendanceStatus(AttendanceStatus.ATTENDED);
        return appointmentRepository.save(appt);
    }

    /**
     * Records an actual patient no-show.
     */
    public Appointment recordNoShow(Long appointmentId) {
        Appointment appt = findAppointmentOrThrow(appointmentId);
        appt.setAttendanceStatus(AttendanceStatus.NO_SHOW);
        return appointmentRepository.save(appt);
    }

    /**
     * Cancels an appointment.
     */
    public Appointment cancelAppointment(Long appointmentId) {
        Appointment appt = findAppointmentOrThrow(appointmentId);
        if (appt.getAttendanceStatus().isFinished()) {
            throw new ClinicFlowException("Cannot cancel an appointment that is already " + appt.getAttendanceStatus());
        }
        appt.setAttendanceStatus(AttendanceStatus.CANCELLED);
        return appointmentRepository.save(appt);
    }

    /**
     * Updates attendance outcome (backward compatibility).
     */
    public Appointment recordAttendanceOutcome(Long appointmentId, AttendanceStatus status) {
        Appointment appointment = findAppointmentOrThrow(appointmentId);
        appointment.setAttendanceStatus(status);
        return appointmentRepository.save(appointment);
    }

    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    public Optional<Appointment> getAppointmentById(Long id) {
        return appointmentRepository.findById(id);
    }

    public List<Appointment> getAppointmentsForDate(LocalDate date) {
        return appointmentRepository.findByAppointmentDay(date);
    }

    /**
     * Retrieves today's active smart queue.
     * FAIRNESS RULE: Queue order is strictly FIFO based on check-in arrival time / appointment time.
     * High-risk patients are NEVER demoted in the queue.
     */
    public List<Appointment> getTodayQueue(LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        return appointmentRepository.findAll().stream()
                .filter(a -> a.getAppointmentDay().equals(target))
                .filter(a -> a.getAttendanceStatus().isInQueue())
                .sorted(Comparator.comparing(Appointment::getCheckInTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Appointment::getAppointmentTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    public List<Appointment> getAppointmentsByFilter(String filter) {
        LocalDate today = LocalDate.now();
        List<Appointment> all = appointmentRepository.findAll();

        if (filter == null || "ALL".equalsIgnoreCase(filter)) {
            return all;
        }

        switch (filter.toUpperCase()) {
            case "TODAY":
                return all.stream().filter(a -> a.getAppointmentDay().equals(today)).collect(Collectors.toList());
            case "UPCOMING":
                return all.stream().filter(a -> !a.getAppointmentDay().isBefore(today) && !a.getAttendanceStatus().isFinished()).collect(Collectors.toList());
            case "COMPLETED":
                return all.stream().filter(a -> a.getAttendanceStatus() == AttendanceStatus.ATTENDED).collect(Collectors.toList());
            case "CANCELLED":
                return all.stream().filter(a -> a.getAttendanceStatus() == AttendanceStatus.CANCELLED).collect(Collectors.toList());
            case "NO_SHOW":
            case "MISSED":
                return all.stream().filter(a -> a.getAttendanceStatus() == AttendanceStatus.NO_SHOW).collect(Collectors.toList());
            case "HIGH_RISK":
                return all.stream()
                        .filter(a -> a.getPredictionResult() != null && a.getPredictionResult().getRiskCategory() == RiskCategory.HIGH)
                        .collect(Collectors.toList());
            default:
                return all;
        }
    }

    private Appointment findAppointmentOrThrow(Long appointmentId) {
        if (appointmentId == null) {
            throw new IllegalArgumentException("Appointment ID cannot be null");
        }
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new EntityNotFoundException("Appointment with ID " + appointmentId + " not found"));
    }

    public NoShowPredictionService getPredictionService() {
        return predictionService;
    }

    /**
     * Value container representing an alternative appointment slot evaluated by the ML engine.
     */
    public static class LowerRiskSlot {
        private final LocalDate date;
        private final LocalTime time;
        private final long leadDays;
        private final PredictionResult prediction;

        public LowerRiskSlot(LocalDate date, LocalTime time, long leadDays, PredictionResult prediction) {
            this.date = date;
            this.time = time;
            this.leadDays = leadDays;
            this.prediction = prediction;
        }

        public LocalDate getDate() { return date; }
        public LocalTime getTime() { return time; }
        public long getLeadDays() { return leadDays; }
        public PredictionResult getPrediction() { return prediction; }
        public double getNoShowProbability() { return prediction.getNoShowProbability(); }
        public RiskCategory getRiskCategory() { return prediction.getRiskCategory(); }
        public ReminderAction getSuggestedAction() { return prediction.getSuggestedAction(); }
    }

    /**
     * Recommends alternative appointment dates/slots where the patient's predicted no-show risk
     * is reduced (ideally LOW risk, < 20%), scanning upcoming days starting from the base booking date.
     */
    public List<LowerRiskSlot> findLowerRiskSlots(Patient patient, Doctor doctor, LocalDate bookingDate, int daysToScan, String neighbourhood) {
        if (patient == null || predictionService == null || !predictionService.isModelAvailable()) {
            return Collections.emptyList();
        }

        List<LowerRiskSlot> candidates = new ArrayList<>();
        LocalDate baseDate = bookingDate != null ? bookingDate : LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        String nhood = (neighbourhood != null && !neighbourhood.isBlank()) ? neighbourhood : "JARDIM DA PENHA";

        for (int dayOffset = 0; dayOffset <= daysToScan; dayOffset++) {
            LocalDate candidateDate = baseDate.plusDays(dayOffset);
            // Skip Sunday when outpatient clinics are typically closed
            if (candidateDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                continue;
            }

            // Construct transient appointment for inference
            Appointment temp = new Appointment(999900L + dayOffset, patient, doctor, now, candidateDate, nhood);
            PredictionResult pred = predictionService.predict(temp);

            if (pred.isModelAvailable()) {
                candidates.add(new LowerRiskSlot(candidateDate, LocalTime.of(9, 30), dayOffset, pred));
            }
        }

        // Sort by no-show probability ascending (lowest risk first)
        candidates.sort(Comparator.comparingDouble(LowerRiskSlot::getNoShowProbability));
        return candidates;
    }
}
