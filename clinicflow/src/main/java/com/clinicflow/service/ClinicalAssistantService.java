package com.clinicflow.service;

import com.clinicflow.model.*;
import com.clinicflow.util.ValidationException;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Context-aware Smart Clinical Assistant service.
 * Inspects live clinic state (patients, doctors, appointments, ML risk scores,
 * FIFO queue load, and reminder history) to perform multi-step logical decision-making
 * and generate actionable, advisory-only recommendations.
 */
public class ClinicalAssistantService {

    private final PatientService patientService;
    private final DoctorService doctorService;
    private final AppointmentService appointmentService;
    private final ReminderService reminderService;
    private final NoShowPredictionService predictionService;

    public ClinicalAssistantService(PatientService patientService,
                                    DoctorService doctorService,
                                    AppointmentService appointmentService,
                                    ReminderService reminderService,
                                    NoShowPredictionService predictionService) {
        this.patientService = Objects.requireNonNull(patientService);
        this.doctorService = Objects.requireNonNull(doctorService);
        this.appointmentService = Objects.requireNonNull(appointmentService);
        this.reminderService = Objects.requireNonNull(reminderService);
        this.predictionService = Objects.requireNonNull(predictionService);
    }

    /**
     * Evaluates a natural-language coordinator query against live clinic state.
     */
    public Map<String, Object> answerQuery(String rawQuery, Long optionalPatientId) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            throw new ValidationException("query is required and cannot be empty");
        }
        String query = rawQuery.trim();
        String lower = query.toLowerCase(Locale.ROOT);

        reminderService.syncRemindersWithAppointments();
        List<Patient> patients = patientService.getAllPatients();
        List<Doctor> doctors = doctorService.getAllDoctors();
        List<Appointment> allAppts = appointmentService.getAllAppointments();
        List<Appointment> activeAppts = allAppts.stream()
                .filter(a -> !a.getAttendanceStatus().isFinished())
                .collect(Collectors.toList());
        List<Appointment> todayQueue = appointmentService.getTodayQueue(LocalDate.now());
        List<ReminderRecord> pendingReminders = reminderService.getPendingReminders();

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("totalPatients", patients.size());
        context.put("totalDoctors", doctors.size());
        context.put("activeAppointments", activeAppts.size());
        context.put("todayQueueLoad", todayQueue.size());
        context.put("pendingReminders", pendingReminders.size());
        context.put("modelAvailable", predictionService.isModelAvailable());

        if (containsAny(lower, "doctor", "overload", "load", "balance", "capacity", "physician")) {
            return buildDoctorLoadResponse(doctors, activeAppts, context);
        }
        if (containsAny(lower, "reminder", "outreach", "sms", "call", "notify", "plan")) {
            return buildReminderPlanResponse(pendingReminders, activeAppts, context);
        }
        if (containsAny(lower, "slot", "reschedule", "optimal", "best day", "when", "low-risk", "low risk")) {
            return buildOptimalSlotResponse(patients, doctors, optionalPatientId, context);
        }
        if (containsAny(lower, "high", "risk", "no-show", "noshow", "miss", "priority", "who")) {
            return buildHighRiskTriageResponse(activeAppts, pendingReminders, todayQueue.size(), context);
        }
        return buildClinicBriefingResponse(activeAppts, pendingReminders, doctors, todayQueue.size(), context);
    }

    private Map<String, Object> buildHighRiskTriageResponse(List<Appointment> activeAppts,
                                                            List<ReminderRecord> pendingReminders,
                                                            int queueLoad,
                                                            Map<String, Object> context) {
        List<Appointment> sorted = activeAppts.stream()
                .filter(a -> a.getPredictionResult() != null && a.getPredictionResult().isModelAvailable())
                .sorted((a, b) -> Double.compare(
                        b.getPredictionResult().getNoShowProbability(),
                        a.getPredictionResult().getNoShowProbability()))
                .collect(Collectors.toList());

        List<String> reasoning = new ArrayList<>();
        reasoning.add("Inspected " + activeAppts.size() + " active/upcoming appointments using Weka Random Forest probabilities.");
        reasoning.add("Ranked bookings by predicted no-show probability descending (HIGH >= 50%, MEDIUM >= 20%).");
        reasoning.add("Verified today's FIFO queue load (" + queueLoad + " active); advisory outreach only—no patient is demoted.");

        Map<Long, ReminderRecord> pendingByAppt = pendingReminders.stream()
                .collect(Collectors.toMap(ReminderRecord::getAppointmentId, r -> r, (a, b) -> a));

        List<Map<String, Object>> actions = new ArrayList<>();
        for (Appointment a : sorted) {
            if (actions.size() >= 5) break;
            PredictionResult p = a.getPredictionResult();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("appointmentId", a.getAppointmentId());
            item.put("patientName", a.getPatient() != null ? a.getPatient().getName() : "Unknown");
            item.put("riskCategory", p.getRiskCategory().name());
            item.put("noShowProbability", p.getNoShowProbability());
            item.put("leadDays", a.getLeadDays());
            item.put("detail", String.format(Locale.US, "%s | %s | %dd lead | %.1f%% no-show risk -> %s",
                    a.getQueueToken(), a.getAppointmentDay(), a.getLeadDays(),
                    p.getNoShowProbability() * 100.0, p.getSuggestedAction().name()));

            ReminderRecord rem = pendingByAppt.get(a.getAppointmentId());
            if (rem != null) {
                item.put("oneClickAction", Map.of(
                        "type", "SEND_REMINDER",
                        "label", "Send " + p.getSuggestedAction().name() + " Now",
                        "endpoint", "/api/reminders/send",
                        "reminderId", rem.getReminderId()
                ));
            }
            actions.add(item);
        }

        long elevated = sorted.stream()
                .filter(a -> a.getPredictionResult().getRiskCategory() != RiskCategory.LOW)
                .count();
        String answer = sorted.isEmpty()
                ? "No active scored appointments found. Book an appointment or run the live simulator to generate risk scores."
                : String.format(Locale.US, "Found %d active scored appointments (%d MEDIUM/HIGH risk). Top priority is %s (%.1f%% no-show risk).",
                        sorted.size(), elevated,
                        sorted.get(0).getPatient() != null ? sorted.get(0).getPatient().getName() : "Patient",
                        sorted.get(0).getPredictionResult().getNoShowProbability() * 100.0);

        return assembleResponse("HIGH_RISK_TRIAGE", answer, reasoning, actions, context);
    }

    private Map<String, Object> buildDoctorLoadResponse(List<Doctor> doctors,
                                                        List<Appointment> activeAppts,
                                                        Map<String, Object> context) {
        Map<Long, Long> counts = activeAppts.stream()
                .filter(a -> a.getDoctor() != null)
                .collect(Collectors.groupingBy(a -> a.getDoctor().getId(), Collectors.counting()));

        List<Doctor> sortedDocs = doctors.stream()
                .sorted((a, b) -> Long.compare(counts.getOrDefault(b.getId(), 0L), counts.getOrDefault(a.getId(), 0L)))
                .collect(Collectors.toList());

        List<String> reasoning = List.of(
                "Aggregated " + activeAppts.size() + " active appointments across " + doctors.size() + " registered physicians.",
                "Compared active slot load per physician to spot potential queue bottlenecks.",
                "Checked physician availability flags before recommending load balancing."
        );

        List<Map<String, Object>> actions = new ArrayList<>();
        for (Doctor d : sortedDocs) {
            long load = counts.getOrDefault(d.getId(), 0L);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("doctorId", d.getId());
            item.put("doctorName", d.getName());
            item.put("specialty", d.getSpecialty());
            item.put("activeLoad", load);
            item.put("detail", String.format(Locale.US, "%s (%s, %s) — %d active appointments | %s",
                    d.getName(), d.getSpecialty(), d.getRoomNumber(), load,
                    d.isAvailable() ? "Available for backfill" : "Unavailable"));
            actions.add(item);
        }

        String answer = sortedDocs.isEmpty()
                ? "No doctors registered yet."
                : String.format(Locale.US, "Busiest physician: %s (%d active). Lightest available: %s (%d active).",
                        sortedDocs.get(0).getName(), counts.getOrDefault(sortedDocs.get(0).getId(), 0L),
                        sortedDocs.get(sortedDocs.size() - 1).getName(),
                        counts.getOrDefault(sortedDocs.get(sortedDocs.size() - 1).getId(), 0L));

        return assembleResponse("DOCTOR_LOAD_BALANCING", answer, reasoning, actions, context);
    }

    private Map<String, Object> buildReminderPlanResponse(List<ReminderRecord> pendingReminders,
                                                           List<Appointment> activeAppts,
                                                           Map<String, Object> context) {
        List<ReminderRecord> sorted = pendingReminders.stream()
                .sorted(Comparator.comparingInt((ReminderRecord r) -> r.getRiskCategory() == RiskCategory.HIGH ? 0
                        : r.getRiskCategory() == RiskCategory.MEDIUM ? 1 : 2))
                .collect(Collectors.toList());

        List<String> reasoning = List.of(
                "Synchronized " + activeAppts.size() + " upcoming appointments with the reminder outreach queue.",
                "Prioritized HIGH-risk bookings (PHONE_CALL) ahead of MEDIUM (CONFIRMATION_LINK) and LOW (STANDARD).",
                "All reminders remain simulated and non-punitive."
        );

        List<Map<String, Object>> actions = new ArrayList<>();
        for (ReminderRecord r : sorted) {
            if (actions.size() >= 5) break;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("reminderId", r.getReminderId());
            item.put("patientName", r.getPatientName());
            item.put("riskCategory", r.getRiskCategory().name());
            item.put("recommendedAction", r.getRecommendedAction().name());
            item.put("detail", String.format(Locale.US, "%s on %s — %s (%s)",
                    r.getPatientName(), r.getAppointmentDate(), r.getRecommendedAction().name(), r.getRiskCategory().name()));
            item.put("oneClickAction", Map.of(
                    "type", "SEND_REMINDER",
                    "label", "Dispatch " + r.getRecommendedAction().name(),
                    "endpoint", "/api/reminders/send",
                    "reminderId", r.getReminderId()
            ));
            actions.add(item);
        }

        String answer = sorted.isEmpty()
                ? "All upcoming appointments already have reminders dispatched (0 pending)."
                : String.format(Locale.US, "%d pending reminder(s) queued. Highest urgency: %s (%s -> %s).",
                        sorted.size(), sorted.get(0).getPatientName(),
                        sorted.get(0).getRiskCategory().name(), sorted.get(0).getRecommendedAction().name());

        return assembleResponse("REMINDER_ACTION_PLAN", answer, reasoning, actions, context);
    }

    private Map<String, Object> buildOptimalSlotResponse(List<Patient> patients,
                                                         List<Doctor> doctors,
                                                         Long optionalPatientId,
                                                         Map<String, Object> context) {
        Patient target = patients.stream()
                .filter(p -> optionalPatientId != null && p.getId().equals(optionalPatientId))
                .findFirst()
                .orElse(patients.isEmpty() ? new Patient(0L, "Walk-in Profile", Gender.F, 32) : patients.get(0));
        Doctor doctor = doctors.isEmpty()
                ? new Doctor(201L, "Dr. Beatriz Silva", Gender.F, 42, "Cardiology", "Room 201")
                : doctors.get(0);

        List<AppointmentService.LowerRiskSlot> slots = appointmentService.findLowerRiskSlots(
                target, doctor, LocalDate.now(), 10, "CENTRO");

        List<String> reasoning = List.of(
                "Scanned upcoming 10 clinic days (excluding Sundays) for patient '" + target.getName() + "' (Age " + target.getAge() + ").",
                "Evaluated each candidate date with Weka Random Forest using leak-free booking features.",
                "Sorted candidate dates by lowest predicted no-show probability."
        );

        List<Map<String, Object>> actions = new ArrayList<>();
        for (AppointmentService.LowerRiskSlot slot : slots) {
            if (actions.size() >= 3) break;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", slot.getDate().toString());
            item.put("time", slot.getTime().toString());
            item.put("riskCategory", slot.getRiskCategory().name());
            item.put("noShowProbability", slot.getNoShowProbability());
            item.put("detail", String.format(Locale.US, "%s (%s) at %s — %dd lead | %.1f%% no-show (%s)",
                    slot.getDate(), slot.getDate().getDayOfWeek(), slot.getTime(),
                    slot.getLeadDays(), slot.getNoShowProbability() * 100.0, slot.getRiskCategory().name()));
            item.put("oneClickAction", Map.of(
                    "type", "APPLY_SLOT",
                    "label", "Use " + slot.getDate() + " in Booking",
                    "date", slot.getDate().toString(),
                    "time", slot.getTime().toString()
            ));
            actions.add(item);
        }

        String answer = actions.isEmpty()
                ? "ML model unavailable to scan alternative slots."
                : String.format(Locale.US, "Best slot for %s: %s (%.1f%% no-show risk, %s).",
                        target.getName(), slots.get(0).getDate(),
                        slots.get(0).getNoShowProbability() * 100.0, slots.get(0).getRiskCategory().name());

        return assembleResponse("OPTIMAL_SLOT_FINDER", answer, reasoning, actions, context);
    }

    private Map<String, Object> buildClinicBriefingResponse(List<Appointment> activeAppts,
                                                            List<ReminderRecord> pendingReminders,
                                                            List<Doctor> doctors,
                                                            int queueLoad,
                                                            Map<String, Object> context) {
        long highRisk = activeAppts.stream()
                .filter(a -> a.getPredictionResult() != null && a.getPredictionResult().getRiskCategory() == RiskCategory.HIGH)
                .count();
        long medRisk = activeAppts.stream()
                .filter(a -> a.getPredictionResult() != null && a.getPredictionResult().getRiskCategory() == RiskCategory.MEDIUM)
                .count();

        List<String> reasoning = List.of(
                "Analyzed " + activeAppts.size() + " active appointments across " + doctors.size() + " doctors.",
                "Detected " + highRisk + " HIGH-risk and " + medRisk + " MEDIUM-risk bookings requiring proactive outreach.",
                "Verified " + queueLoad + " patients in today's strict FIFO queue and " + pendingReminders.size() + " pending reminder(s)."
        );

        String answer = String.format(Locale.US,
                "Clinic Briefing: %d active appointment(s) (%d HIGH, %d MEDIUM risk), %d in today's FIFO queue, and %d pending reminder(s).",
                activeAppts.size(), highRisk, medRisk, queueLoad, pendingReminders.size());

        List<Map<String, Object>> actions = new ArrayList<>();
        if (!pendingReminders.isEmpty()) {
            ReminderRecord first = pendingReminders.get(0);
            actions.add(Map.of(
                    "detail", "Priority outreach: " + first.getPatientName() + " (" + first.getRiskCategory() + " -> " + first.getRecommendedAction() + ")",
                    "oneClickAction", Map.of(
                            "type", "SEND_REMINDER",
                            "label", "Send Next Priority Reminder",
                            "endpoint", "/api/reminders/send",
                            "reminderId", first.getReminderId()
                    )
            ));
        }

        return assembleResponse("CLINIC_BRIEFING", answer, reasoning, actions, context);
    }

    private Map<String, Object> assembleResponse(String intent, String answer,
                                                 List<String> reasoningSteps,
                                                 List<Map<String, Object>> recommendedActions,
                                                 Map<String, Object> clinicContext) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("intent", intent);
        resp.put("answer", answer);
        resp.put("reasoningSteps", reasoningSteps);
        resp.put("recommendedActions", recommendedActions);
        resp.put("clinicContext", clinicContext);
        return resp;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
