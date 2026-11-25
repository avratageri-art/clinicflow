package com.clinicflow.service;

import com.clinicflow.model.*;
import com.clinicflow.util.ValidationException;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Context-aware Smart Clinical Assistant service.
 * Evaluates live operational signals across patients, doctors, appointments,
 * ML probabilities, FIFO queue depth, and reminder history to surface
 * bottlenecks and actionable, advisory-only recommendations.
 */
public class ClinicalAssistantService {

    private static final int MAX_TRIAGE_ITEMS = 5;
    private static final int MAX_SLOT_ITEMS = 3;
    private static final int SLOT_SCAN_DAYS = 10;
    private static final int DOCTOR_TOKEN_MIN_LEN = 4;

    private final PatientService patientService;
    private final DoctorService doctorService;
    private final AppointmentService appointmentService;
    private final ReminderService reminderService;
    private final NoShowPredictionService predictionService;

    /**
     * Constructs a {@link ClinicalAssistantService} with required domain services.
     *
     * @param patientService     patient directory service
     * @param doctorService      doctor directory service
     * @param appointmentService appointment and queue service
     * @param reminderService    reminder outreach service
     * @param predictionService  ML no-show prediction service
     */
    public ClinicalAssistantService(PatientService patientService,
                                    DoctorService doctorService,
                                    AppointmentService appointmentService,
                                    ReminderService reminderService,
                                    NoShowPredictionService predictionService) {
        this.patientService = Objects.requireNonNull(patientService, "patientService cannot be null");
        this.doctorService = Objects.requireNonNull(doctorService, "doctorService cannot be null");
        this.appointmentService = Objects.requireNonNull(appointmentService, "appointmentService cannot be null");
        this.reminderService = Objects.requireNonNull(reminderService, "reminderService cannot be null");
        this.predictionService = Objects.requireNonNull(predictionService, "predictionService cannot be null");
    }

    /**
     * Evaluates a natural-language coordinator query against live clinic state.
     *
     * @param rawQuery          coordinator prompt (non-empty)
     * @param optionalPatientId optional target patient ID for slot/risk queries
     * @return structured response with intent, answer, reasoningSteps, recommendedActions, clinicContext, and contextSignals
     */
    public Map<String, Object> answerQuery(String rawQuery, Long optionalPatientId) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            throw new ValidationException("query is required and cannot be empty");
        }
        String lower = rawQuery.trim().toLowerCase(Locale.ROOT);
        reminderService.syncRemindersWithAppointments();

        List<Patient> patients = patientService.getAllPatients();
        List<Doctor> doctors = doctorService.getAllDoctors();
        List<Appointment> allAppts = appointmentService.getAllAppointments();
        List<Appointment> activeAppts = allAppts.stream().filter(a -> !a.getAttendanceStatus().isFinished()).toList();
        List<Appointment> todayQueue = appointmentService.getTodayQueue(LocalDate.now());
        List<ReminderRecord> pending = reminderService.getPendingReminders();

        Map<Long, Patient> patientById = patients.stream()
                .collect(Collectors.toMap(Patient::getId, p -> p, (a, b) -> a));
        Map<Long, Doctor> doctorById = doctors.stream()
                .collect(Collectors.toMap(Doctor::getId, d -> d, (a, b) -> a));

        Map<String, Object> context = buildClinicContext(patients, doctors, activeAppts, todayQueue, pending);
        Map<String, Object> signals = buildContextSignals(allAppts, activeAppts, todayQueue, pending, doctors);
        return dispatchIntent(lower, optionalPatientId, patients, doctors, patientById, doctorById,
                allAppts, activeAppts, todayQueue, pending, context, signals);
    }

    private Map<String, Object> dispatchIntent(String lower, Long optionalPatientId,
                                               List<Patient> patients, List<Doctor> doctors,
                                               Map<Long, Patient> patientById, Map<Long, Doctor> doctorById,
                                               List<Appointment> allAppts, List<Appointment> activeAppts,
                                               List<Appointment> todayQueue, List<ReminderRecord> pending,
                                               Map<String, Object> context, Map<String, Object> signals) {
        Optional<Doctor> doc = findMentionedDoctor(doctors, doctorById, lower);
        if (doc.isPresent()) {
            return buildSingleDoctorContextResponse(doc.get(), activeAppts, context, signals);
        }
        if (containsAny(lower, "doctor", "overload", "load", "balance", "capacity", "physician")) {
            return buildDoctorLoadResponse(doctors, activeAppts, context, signals);
        }
        Optional<Patient> pat = findMentionedPatient(patients, patientById, lower);
        if (pat.isPresent()) {
            return buildSinglePatientContextResponse(pat.get(), allAppts, pending, doctors, context, signals);
        }
        if (containsAny(lower, "reminder", "outreach", "sms", "call", "notify", "plan")) {
            return buildReminderPlanResponse(pending, activeAppts, context, signals);
        }
        if (containsAny(lower, "slot", "reschedule", "optimal", "best day", "when", "low-risk", "low risk")) {
            return buildOptimalSlotResponse(patients, doctors, optionalPatientId, context, signals);
        }
        if (containsAny(lower, "high", "risk", "no-show", "noshow", "miss", "priority", "who")) {
            return buildHighRiskTriageResponse(activeAppts, pending, todayQueue.size(), context, signals);
        }
        return buildClinicBriefingResponse(activeAppts, pending, doctors, todayQueue.size(), context, signals);
    }

    private Map<String, Object> buildClinicContext(List<Patient> patients, List<Doctor> doctors,
                                                   List<Appointment> activeAppts, List<Appointment> todayQueue,
                                                   List<ReminderRecord> pending) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("totalPatients", patients.size());
        ctx.put("totalDoctors", doctors.size());
        ctx.put("activeAppointments", activeAppts.size());
        ctx.put("todayQueueLoad", todayQueue.size());
        ctx.put("pendingReminders", pending.size());
        ctx.put("modelAvailable", predictionService.isModelAvailable());
        return ctx;
    }

    private Map<String, Object> buildContextSignals(List<Appointment> allAppts, List<Appointment> activeAppts,
                                                    List<Appointment> todayQueue, List<ReminderRecord> pending,
                                                    List<Doctor> doctors) {
        long highRisk = allAppts.stream()
                .filter(a -> a.getPredictionResult() != null && a.getPredictionResult().getRiskCategory() == RiskCategory.HIGH)
                .count();
        Set<Long> pendingApptIds = pending.stream().map(ReminderRecord::getAppointmentId).collect(Collectors.toSet());
        long unremindedHigh = activeAppts.stream()
                .filter(a -> a.getPredictionResult() != null && a.getPredictionResult().getRiskCategory() == RiskCategory.HIGH)
                .filter(a -> pendingApptIds.contains(a.getAppointmentId()))
                .count();

        Map<Long, Long> loadByDoc = countActiveByDoctor(activeAppts);
        Doctor busiest = doctors.stream()
                .max(Comparator.comparingLong(d -> loadByDoc.getOrDefault(d.getId(), 0L)))
                .orElse(null);

        Map<String, Object> signals = new LinkedHashMap<>();
        signals.put("totalAppointments", allAppts.size());
        signals.put("highRiskCount", highRisk);
        signals.put("unremindedHighRiskCount", unremindedHigh);
        signals.put("activeQueueDepth", todayQueue.size());
        signals.put("busiestDoctor", busiest != null ? busiest.getName() : "None");
        signals.put("busiestDoctorLoad", busiest != null ? loadByDoc.getOrDefault(busiest.getId(), 0L) : 0L);
        return signals;
    }

    private Map<String, Object> buildHighRiskTriageResponse(List<Appointment> activeAppts,
                                                            List<ReminderRecord> pending, int queueLoad,
                                                            Map<String, Object> context, Map<String, Object> signals) {
        List<Appointment> sorted = activeAppts.stream()
                .filter(a -> a.getPredictionResult() != null && a.getPredictionResult().isModelAvailable())
                .sorted((a, b) -> Double.compare(b.getPredictionResult().getNoShowProbability(), a.getPredictionResult().getNoShowProbability()))
                .toList();
        Map<Long, ReminderRecord> pendingByAppt = pending.stream()
                .collect(Collectors.toMap(ReminderRecord::getAppointmentId, r -> r, (a, b) -> a));

        List<Map<String, Object>> actions = sorted.stream().limit(MAX_TRIAGE_ITEMS)
                .map(a -> toTriageActionItem(a, pendingByAppt.get(a.getAppointmentId()))).toList();
        long elevated = sorted.stream().filter(a -> a.getPredictionResult().getRiskCategory() != RiskCategory.LOW).count();
        String answer = sorted.isEmpty()
                ? "No active scored appointments found. Book an appointment or run the live simulator to generate risk scores."
                : String.format(Locale.US, "Found %d active scored appointments (%d MEDIUM/HIGH risk). Top priority is %s (%.1f%% no-show risk).",
                        sorted.size(), elevated, patientName(sorted.get(0)),
                        sorted.get(0).getPredictionResult().getNoShowProbability() * 100.0);
        List<String> reasoning = List.of(
                "Inspected " + activeAppts.size() + " active/upcoming appointments using Weka Random Forest probabilities.",
                "Ranked bookings by predicted no-show probability descending (HIGH >= 50%, MEDIUM >= 20%).",
                "Verified today's FIFO queue load (" + queueLoad + " active); advisory outreach only—no patient is demoted."
        );
        return assembleResponse("HIGH_RISK_TRIAGE", answer, reasoning, actions, context, signals);
    }

    private Map<String, Object> toTriageActionItem(Appointment a, ReminderRecord rem) {
        PredictionResult p = a.getPredictionResult();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("appointmentId", a.getAppointmentId());
        item.put("patientName", patientName(a));
        item.put("riskCategory", p.getRiskCategory().name());
        item.put("noShowProbability", p.getNoShowProbability());
        item.put("leadDays", a.getLeadDays());
        item.put("detail", String.format(Locale.US, "%s | %s | %dd lead | %.1f%% no-show risk -> %s",
                a.getQueueToken(), a.getAppointmentDay(), a.getLeadDays(), p.getNoShowProbability() * 100.0, p.getSuggestedAction().name()));
        if (rem != null) {
            item.put("oneClickAction", sendReminderAction("Send " + p.getSuggestedAction().name() + " Now", rem.getReminderId()));
        } else if (a.getAttendanceStatus() == AttendanceStatus.SCHEDULED) {
            item.put("oneClickAction", checkInAction("Check In " + patientName(a), a.getAppointmentId()));
        }
        return item;
    }

    private Map<String, Object> buildDoctorLoadResponse(List<Doctor> doctors, List<Appointment> activeAppts,
                                                        Map<String, Object> context, Map<String, Object> signals) {
        Map<Long, Long> counts = countActiveByDoctor(activeAppts);
        List<Doctor> sorted = doctors.stream()
                .sorted((a, b) -> Long.compare(counts.getOrDefault(b.getId(), 0L), counts.getOrDefault(a.getId(), 0L)))
                .toList();
        List<Map<String, Object>> actions = sorted.stream().map(d -> {
            long load = counts.getOrDefault(d.getId(), 0L);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("doctorId", d.getId());
            item.put("doctorName", d.getName());
            item.put("specialty", d.getSpecialty());
            item.put("activeLoad", load);
            item.put("detail", String.format(Locale.US, "%s (%s, %s) — %d active appointments | %s",
                    d.getName(), d.getSpecialty(), d.getRoomNumber(), load, d.isAvailable() ? "Available for backfill" : "Unavailable"));
            return item;
        }).toList();
        String answer = sorted.isEmpty() ? "No doctors registered yet."
                : String.format(Locale.US, "Busiest physician: %s (%d active). Lightest available: %s (%d active).",
                        sorted.get(0).getName(), counts.getOrDefault(sorted.get(0).getId(), 0L),
                        sorted.get(sorted.size() - 1).getName(), counts.getOrDefault(sorted.get(sorted.size() - 1).getId(), 0L));
        List<String> reasoning = List.of(
                "Aggregated " + activeAppts.size() + " active appointments across " + doctors.size() + " registered physicians.",
                "Compared active slot load per physician to spot potential queue bottlenecks.",
                "Checked physician availability flags before recommending load balancing."
        );
        return assembleResponse("DOCTOR_LOAD_BALANCING", answer, reasoning, actions, context, signals);
    }

    private Map<String, Object> buildReminderPlanResponse(List<ReminderRecord> pending, List<Appointment> activeAppts,
                                                           Map<String, Object> context, Map<String, Object> signals) {
        List<ReminderRecord> sorted = pending.stream()
                .sorted(Comparator.comparingInt(r -> r.getRiskCategory() == RiskCategory.HIGH ? 0
                        : r.getRiskCategory() == RiskCategory.MEDIUM ? 1 : 2))
                .toList();
        List<Map<String, Object>> actions = sorted.stream().limit(MAX_TRIAGE_ITEMS).map(r -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("reminderId", r.getReminderId());
            item.put("patientName", r.getPatientName());
            item.put("riskCategory", r.getRiskCategory().name());
            item.put("recommendedAction", r.getRecommendedAction().name());
            item.put("detail", String.format(Locale.US, "%s on %s — %s (%s)",
                    r.getPatientName(), r.getAppointmentDate(), r.getRecommendedAction().name(), r.getRiskCategory().name()));
            item.put("oneClickAction", sendReminderAction("Dispatch " + r.getRecommendedAction().name(), r.getReminderId()));
            return item;
        }).toList();
        String answer = sorted.isEmpty() ? "All upcoming appointments already have reminders dispatched (0 pending)."
                : String.format(Locale.US, "%d pending reminder(s) queued. Highest urgency: %s (%s -> %s).",
                        sorted.size(), sorted.get(0).getPatientName(), sorted.get(0).getRiskCategory().name(), sorted.get(0).getRecommendedAction().name());
        List<String> reasoning = List.of(
                "Synchronized " + activeAppts.size() + " upcoming appointments with the reminder outreach queue.",
                "Prioritized HIGH-risk bookings (PHONE_CALL) ahead of MEDIUM (CONFIRMATION_LINK) and LOW (STANDARD).",
                "All reminders remain simulated and non-punitive."
        );
        return assembleResponse("REMINDER_ACTION_PLAN", answer, reasoning, actions, context, signals);
    }

    private Map<String, Object> buildOptimalSlotResponse(List<Patient> patients, List<Doctor> doctors,
                                                         Long optionalPatientId, Map<String, Object> context,
                                                         Map<String, Object> signals) {
        Patient target = patients.stream()
                .filter(p -> optionalPatientId != null && p.getId().equals(optionalPatientId))
                .findFirst()
                .orElse(patients.isEmpty() ? new Patient(0L, "Walk-in Profile", Gender.F, 32) : patients.get(0));
        Doctor doc = doctors.isEmpty() ? new Doctor(201L, "Dr. Beatriz Silva", Gender.F, 42, "Cardiology", "Room 201") : doctors.get(0);
        List<AppointmentService.LowerRiskSlot> slots = appointmentService.findLowerRiskSlots(target, doc, LocalDate.now(), SLOT_SCAN_DAYS, "CENTRO");
        List<Map<String, Object>> actions = slots.stream().limit(MAX_SLOT_ITEMS).map(slot -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", slot.getDate().toString());
            item.put("time", slot.getTime().toString());
            item.put("riskCategory", slot.getRiskCategory().name());
            item.put("noShowProbability", slot.getNoShowProbability());
            item.put("detail", String.format(Locale.US, "%s (%s) at %s — %dd lead | %.1f%% no-show (%s)",
                    slot.getDate(), slot.getDate().getDayOfWeek(), slot.getTime(), slot.getLeadDays(),
                    slot.getNoShowProbability() * 100.0, slot.getRiskCategory().name()));
            item.put("oneClickAction", applySlotAction("Use " + slot.getDate() + " in Booking", slot));
            return item;
        }).toList();
        String answer = actions.isEmpty() ? "ML model unavailable to scan alternative slots."
                : String.format(Locale.US, "Best slot for %s: %s (%.1f%% no-show risk, %s).",
                        target.getName(), slots.get(0).getDate(), slots.get(0).getNoShowProbability() * 100.0, slots.get(0).getRiskCategory().name());
        List<String> reasoning = List.of(
                "Scanned upcoming 10 clinic days (excluding Sundays) for patient '" + target.getName() + "' (Age " + target.getAge() + ").",
                "Evaluated each candidate date with Weka Random Forest using leak-free booking features.",
                "Sorted candidate dates by lowest predicted no-show probability."
        );
        return assembleResponse("OPTIMAL_SLOT_FINDER", answer, reasoning, actions, context, signals);
    }

    private Map<String, Object> buildClinicBriefingResponse(List<Appointment> activeAppts, List<ReminderRecord> pending,
                                                            List<Doctor> doctors, int queueLoad,
                                                            Map<String, Object> context, Map<String, Object> signals) {
        long highRisk = activeAppts.stream().filter(a -> a.getPredictionResult() != null && a.getPredictionResult().getRiskCategory() == RiskCategory.HIGH).count();
        long medRisk = activeAppts.stream().filter(a -> a.getPredictionResult() != null && a.getPredictionResult().getRiskCategory() == RiskCategory.MEDIUM).count();
        List<Map<String, Object>> actions = collectBriefingBottlenecks(activeAppts, pending);
        String answer = String.format(Locale.US,
                "Clinic Briefing: %d active appointment(s) (%d HIGH, %d MEDIUM risk), %d in today's FIFO queue, and %d pending reminder(s).",
                activeAppts.size(), highRisk, medRisk, queueLoad, pending.size());
        List<String> reasoning = List.of(
                "Analyzed " + activeAppts.size() + " active appointments across " + doctors.size() + " doctors (busiest: " + signals.get("busiestDoctor") + ").",
                "Detected " + signals.get("unremindedHighRiskCount") + " unreminded HIGH-risk booking(s) and " + pending.size() + " pending reminder(s).",
                "Verified " + queueLoad + " patient(s) in today's strict FIFO queue (advisory AI only; no patient demotion)."
        );
        return assembleResponse("CLINIC_BRIEFING", answer, reasoning, actions, context, signals);
    }

    private List<Map<String, Object>> collectBriefingBottlenecks(List<Appointment> activeAppts, List<ReminderRecord> pending) {
        List<Map<String, Object>> actions = new ArrayList<>();
        pending.stream()
                .sorted(Comparator.comparingInt(r -> r.getRiskCategory() == RiskCategory.HIGH ? 0 : 1))
                .findFirst()
                .ifPresent(r -> actions.add(Map.of(
                        "detail", "Unreminded " + r.getRiskCategory() + " risk: " + r.getPatientName() + " (" + r.getAppointmentDate() + " -> " + r.getRecommendedAction() + ")",
                        "oneClickAction", sendReminderAction("Send " + r.getRecommendedAction() + " Now", r.getReminderId())
                )));
        activeAppts.stream()
                .filter(a -> a.getAttendanceStatus() == AttendanceStatus.SCHEDULED)
                .findFirst()
                .ifPresent(a -> actions.add(Map.of(
                        "detail", "Scheduled arrival ready for check-in: " + patientName(a) + " (" + a.getQueueToken() + ")",
                        "oneClickAction", checkInAction("Check In " + patientName(a), a.getAppointmentId())
                )));
        return actions;
    }

    private Map<String, Object> buildSinglePatientContextResponse(Patient patient, List<Appointment> allAppts,
                                                                    List<ReminderRecord> pending, List<Doctor> doctors,
                                                                    Map<String, Object> context, Map<String, Object> signals) {
        List<Appointment> history = allAppts.stream().filter(a -> a.getPatient() != null && a.getPatient().getId().equals(patient.getId())).toList();
        long noShows = history.stream().filter(a -> a.getAttendanceStatus() == AttendanceStatus.NO_SHOW).count();
        long attended = history.stream().filter(a -> a.getAttendanceStatus() == AttendanceStatus.ATTENDED).count();
        Doctor doc = doctors.isEmpty() ? new Doctor(201L, "Dr. Beatriz Silva", Gender.F, 42, "Cardiology", "Room 201") : doctors.get(0);
        List<AppointmentService.LowerRiskSlot> slots = appointmentService.findLowerRiskSlots(patient, doc, LocalDate.now(), 7, "CENTRO");
        List<Map<String, Object>> actions = new ArrayList<>();
        if (!slots.isEmpty()) {
            AppointmentService.LowerRiskSlot best = slots.get(0);
            actions.add(Map.of("detail", String.format(Locale.US, "Best upcoming slot: %s at %s (%.1f%% no-show, %s)",
                    best.getDate(), best.getTime(), best.getNoShowProbability() * 100.0, best.getRiskCategory()),
                    "oneClickAction", applySlotAction("Book " + best.getDate() + " for " + patient.getName(), best)));
        }
        pending.stream().filter(r -> r.getPatientName().equalsIgnoreCase(patient.getName())).findFirst()
                .ifPresent(r -> actions.add(Map.of("detail", "Pending reminder #" + r.getReminderId() + " (" + r.getRecommendedAction() + ")",
                        "oneClickAction", sendReminderAction("Send " + r.getRecommendedAction() + " Now", r.getReminderId()))));
        String answer = String.format(Locale.US, "Patient %s (ID %d, Age %d): %d appointment(s) on record (%d attended, %d no-show).",
                patient.getName(), patient.getId(), patient.getAge(), history.size(), attended, noShows);
        List<String> reasoning = List.of(
                String.format(Locale.US, "Located patient #%d (%s, Age %d, Chronic conditions: %s).", patient.getId(), patient.getName(), patient.getAge(), patient.hasChronicConditions() ? "Yes" : "No"),
                String.format(Locale.US, "Reviewed %d appointments (%d attended, %d no-show).", history.size(), attended, noShows),
                "Evaluated upcoming 7-day booking risk via Weka Random Forest for personalized outreach."
        );
        return assembleResponse("PATIENT_CONTEXT", answer, reasoning, actions, context, signals);
    }

    private Map<String, Object> buildSingleDoctorContextResponse(Doctor doctor, List<Appointment> activeAppts,
                                                                   Map<String, Object> context, Map<String, Object> signals) {
        List<Appointment> docAppts = activeAppts.stream().filter(a -> a.getDoctor() != null && a.getDoctor().getId().equals(doctor.getId())).toList();
        List<Map<String, Object>> actions = docAppts.stream().limit(4).map(a -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("appointmentId", a.getAppointmentId());
            item.put("detail", String.format(Locale.US, "%s — %s on %s (%s)", a.getQueueToken(), patientName(a), a.getAppointmentDay(), a.getAttendanceStatus()));
            return item;
        }).toList();
        String answer = String.format(Locale.US, "%s (%s, %s) currently has %d active appointment(s) and is %s.",
                doctor.getName(), doctor.getSpecialty(), doctor.getRoomNumber(), docAppts.size(), doctor.isAvailable() ? "available" : "unavailable");
        List<String> reasoning = List.of(
                String.format(Locale.US, "Matched physician %s (ID %d, %s, %s).", doctor.getName(), doctor.getId(), doctor.getSpecialty(), doctor.getRoomNumber()),
                String.format(Locale.US, "Counted %d active/upcoming appointments in current clinic schedule.", docAppts.size()),
                "Availability status: " + (doctor.isAvailable() ? "Available for new or backfill bookings." : "Currently unavailable.")
        );
        return assembleResponse("DOCTOR_CONTEXT", answer, reasoning, actions, context, signals);
    }

    private Map<String, Object> assembleResponse(String intent, String answer, List<String> reasoningSteps,
                                                 List<Map<String, Object>> recommendedActions,
                                                 Map<String, Object> clinicContext, Map<String, Object> contextSignals) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("intent", intent);
        resp.put("answer", answer);
        resp.put("reasoningSteps", reasoningSteps);
        resp.put("recommendedActions", recommendedActions);
        resp.put("clinicContext", clinicContext);
        resp.put("contextSignals", contextSignals);
        return resp;
    }

    private static Map<String, Object> sendReminderAction(String label, Long reminderId) {
        return Map.of("type", "SEND_REMINDER", "label", label, "endpoint", "/api/reminders/send", "reminderId", reminderId);
    }

    private static Map<String, Object> checkInAction(String label, Long appointmentId) {
        return Map.of("type", "CHECK_IN", "label", label, "endpoint", "/api/queue/checkin", "appointmentId", appointmentId);
    }

    private static Map<String, Object> applySlotAction(String label, AppointmentService.LowerRiskSlot slot) {
        return Map.of("type", "APPLY_SLOT", "label", label, "date", slot.getDate().toString(), "time", slot.getTime().toString());
    }

    private static Map<Long, Long> countActiveByDoctor(List<Appointment> activeAppts) {
        return activeAppts.stream().filter(a -> a.getDoctor() != null)
                .collect(Collectors.groupingBy(a -> a.getDoctor().getId(), Collectors.counting()));
    }

    private static String patientName(Appointment a) {
        return a.getPatient() != null ? a.getPatient().getName() : "Unknown";
    }

    private static Optional<Patient> findMentionedPatient(List<Patient> patients, Map<Long, Patient> byId, String lowerQuery) {
        for (String token : lowerQuery.split("\\s+")) {
            try {
                Patient direct = byId.get(Long.parseLong(token.replaceAll("[^0-9]", "")));
                if (direct != null) return Optional.of(direct);
            } catch (NumberFormatException ignored) {}
        }
        return patients.stream().filter(p -> lowerQuery.contains(p.getName().toLowerCase(Locale.ROOT))).findFirst();
    }

    private static Optional<Doctor> findMentionedDoctor(List<Doctor> doctors, Map<Long, Doctor> byId, String lowerQuery) {
        for (String token : lowerQuery.split("\\s+")) {
            try {
                Doctor direct = byId.get(Long.parseLong(token.replaceAll("[^0-9]", "")));
                if (direct != null) return Optional.of(direct);
            } catch (NumberFormatException ignored) {}
        }
        for (Doctor d : doctors) {
            for (String token : d.getName().toLowerCase(Locale.ROOT).replace("dr.", "").trim().split("\\s+")) {
                if (token.length() >= DOCTOR_TOKEN_MIN_LEN && lowerQuery.contains(token)) return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
