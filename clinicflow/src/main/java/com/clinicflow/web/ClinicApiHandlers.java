package com.clinicflow.web;

import com.clinicflow.ml.NoShowModelTrainer;
import com.clinicflow.model.*;
import com.clinicflow.service.*;
import com.clinicflow.util.*;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST API endpoint handlers for {@link ClinicFlowWebServer}.
 * Keeps every handler concise and delegates serialization to {@link ApiResponseMapper}.
 */
final class ClinicApiHandlers {

    private static final Logger LOGGER = Logger.getLogger(ClinicApiHandlers.class.getName());
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final int MAX_NAME_LEN = 120;
    private static final int MAX_TEXT_LEN = 120;
    private static final int MAX_NOTES_LEN = 300;
    private static final int MAX_QUERY_LEN = 500;
    private static final int MAX_AGE = 120;
    private static final int MIN_DOCTOR_AGE = 18;
    private static final int MAX_ALT_SLOTS = 3;
    private static final int SLOT_SCAN_DAYS = 10;
    private static final int HTTP_OK = 200;
    private static final int HTTP_CREATED = 201;
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_METHOD_NOT_ALLOWED = 405;
    private static final int HTTP_CONFLICT = 409;
    private static final int HTTP_PAYLOAD_TOO_LARGE = 413;
    private static final int HTTP_SERVER_ERROR = 500;

    private final PatientService patientService;
    private final DoctorService doctorService;
    private final AppointmentService appointmentService;
    private final ReminderService reminderService;
    private final ModelInsightsService modelInsightsService;
    private final ClinicalAssistantService assistantService;
    private final WekaNoShowPredictionService predictionService;
    private final NoShowModelTrainer modelTrainer;
    private final WebSecuritySupport security;

    ClinicApiHandlers(PatientService patientService,
                      DoctorService doctorService,
                      AppointmentService appointmentService,
                      ReminderService reminderService,
                      ModelInsightsService modelInsightsService,
                      ClinicalAssistantService assistantService,
                      WekaNoShowPredictionService predictionService,
                      NoShowModelTrainer modelTrainer,
                      WebSecuritySupport security) {
        this.patientService = Objects.requireNonNull(patientService);
        this.doctorService = Objects.requireNonNull(doctorService);
        this.appointmentService = Objects.requireNonNull(appointmentService);
        this.reminderService = Objects.requireNonNull(reminderService);
        this.modelInsightsService = Objects.requireNonNull(modelInsightsService);
        this.assistantService = Objects.requireNonNull(assistantService);
        this.predictionService = Objects.requireNonNull(predictionService);
        this.modelTrainer = Objects.requireNonNull(modelTrainer);
        this.security = Objects.requireNonNull(security);
    }

    void handleDashboard(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            security.sendError(ex, HTTP_METHOD_NOT_ALLOWED, "Method not allowed", "GET");
            return;
        }
        List<Appointment> all = appointmentService.getAllAppointments();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalPatients", patientService.getAllPatients().size());
        data.put("totalDoctors", doctorService.getAllDoctors().size());
        data.put("totalAppointments", all.size());
        data.put("todayQueueCount", appointmentService.getTodayQueue(LocalDate.now()).size());
        data.put("highRiskCount", countByRisk(all, RiskCategory.HIGH));
        data.put("mediumRiskCount", countByRisk(all, RiskCategory.MEDIUM));
        data.put("lowRiskCount", countByRisk(all, RiskCategory.LOW));
        data.put("attendedCount", countByStatus(all, AttendanceStatus.ATTENDED));
        data.put("noShowCount", countByStatus(all, AttendanceStatus.NO_SHOW));
        data.put("pendingReminders", reminderService.getPendingReminders().size());
        data.put("modelAvailable", predictionService.isModelAvailable());
        security.sendJson(ex, HTTP_OK, data, true);
    }

    void handlePatients(HttpExchange ex) throws IOException {
        try {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                List<Map<String, Object>> list = patientService.getAllPatients().stream().map(ApiResponseMapper::toPatientMap).toList();
                security.sendJson(ex, HTTP_OK, Map.of("patients", list), true);
            } else if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                Patient saved = registerPatient(readJsonBody(ex));
                security.sendJson(ex, HTTP_CREATED, Map.of("patient", ApiResponseMapper.toPatientMap(saved)), true);
            } else {
                security.sendError(ex, HTTP_METHOD_NOT_ALLOWED, "Method not allowed", "GET, POST");
            }
        } catch (Exception e) { handleApiException(ex, e); }
    }

    void handleDoctors(HttpExchange ex) throws IOException {
        try {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                List<Map<String, Object>> list = doctorService.getAllDoctors().stream().map(ApiResponseMapper::toDoctorMap).toList();
                security.sendJson(ex, HTTP_OK, Map.of("doctors", list), true);
            } else if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                Doctor saved = registerDoctor(readJsonBody(ex));
                security.sendJson(ex, HTTP_CREATED, Map.of("doctor", ApiResponseMapper.toDoctorMap(saved)), true);
            } else {
                security.sendError(ex, HTTP_METHOD_NOT_ALLOWED, "Method not allowed", "GET, POST");
            }
        } catch (Exception e) { handleApiException(ex, e); }
    }

    void handleAppointments(HttpExchange ex) throws IOException {
        try {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                List<Map<String, Object>> list = appointmentService.getAllAppointments().stream().map(ApiResponseMapper::toAppointmentMap).toList();
                security.sendJson(ex, HTTP_OK, Map.of("appointments", list), true);
            } else if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                security.sendJson(ex, HTTP_CREATED, bookAppointment(readJsonBody(ex)), true);
            } else {
                security.sendError(ex, HTTP_METHOD_NOT_ALLOWED, "Method not allowed", "GET, POST");
            }
        } catch (Exception e) { handleApiException(ex, e); }
    }

    void handlePredict(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            security.sendError(ex, HTTP_METHOD_NOT_ALLOWED, "Method not allowed", "POST");
            return;
        }
        try {
            security.sendJson(ex, HTTP_OK, runPredictionSimulation(readJsonBody(ex)), true);
        } catch (Exception e) { handleApiException(ex, e); }
    }

    void handleQueue(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod()) && "/api/queue".equals(path)) {
                List<Map<String, Object>> q = appointmentService.getTodayQueue(LocalDate.now()).stream().map(ApiResponseMapper::toAppointmentMap).toList();
                security.sendJson(ex, HTTP_OK, Map.of("date", LocalDate.now().toString(), "queue", q), true);
            } else if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                long apptId = WebSecuritySupport.requirePositiveId(ApiResponseMapper.getLong(readJsonBody(ex), "appointmentId"), "appointmentId");
                Appointment updated = applyQueueTransition(path, apptId);
                if (updated == null) security.sendError(ex, HTTP_NOT_FOUND, "Unknown queue action", null);
                else security.sendJson(ex, HTTP_OK, Map.of("appointment", ApiResponseMapper.toAppointmentMap(updated)), true);
            } else {
                security.sendError(ex, HTTP_METHOD_NOT_ALLOWED, "Method not allowed", "/api/queue".equals(path) ? "GET" : "POST");
            }
        } catch (Exception e) { handleApiException(ex, e); }
    }

    void handleReminders(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod()) && "/api/reminders".equals(path)) {
                reminderService.syncRemindersWithAppointments();
                List<Map<String, Object>> list = reminderService.getAllReminders().stream().map(ApiResponseMapper::toReminderMap).toList();
                security.sendJson(ex, HTTP_OK, Map.of("reminders", list, "disclaimer", ReminderService.ACADEMIC_DISCLAIMER), true);
            } else if ("POST".equalsIgnoreCase(ex.getRequestMethod()) && "/api/reminders/send".equals(path)) {
                long id = WebSecuritySupport.requirePositiveId(ApiResponseMapper.getLong(readJsonBody(ex), "reminderId"), "reminderId");
                ReminderRecord sent = reminderService.simulateSendReminder(id);
                security.sendJson(ex, HTTP_OK, Map.of("reminderId", sent.getReminderId(), "status", sent.getStatus()), true);
            } else {
                security.sendError(ex, HTTP_METHOD_NOT_ALLOWED, "Method not allowed", "/api/reminders".equals(path) ? "GET" : "POST");
            }
        } catch (Exception e) { handleApiException(ex, e); }
    }

    void handleModel(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod()) && "/api/model/insights".equals(path)) {
                security.sendJson(ex, HTTP_OK, ApiResponseMapper.toModelInsightsMap(modelInsightsService.getModelSpecification(), predictionService.isModelAvailable()), true);
            } else if ("POST".equalsIgnoreCase(ex.getRequestMethod()) && "/api/model/retrain".equals(path)) {
                NoShowModelTrainer.TrainingPipelineResult res = modelTrainer.runPipeline();
                predictionService.reloadModel();
                security.sendJson(ex, HTTP_OK, Map.of("status", "RETRAINED", "selectedModel", res.getSelectedResult().getModelName(),
                        "accuracy", res.getSelectedResult().getAccuracy(), "rocArea", res.getSelectedResult().getRocArea()), true);
            } else if ("/api/model/insights".equals(path) || "/api/model/retrain".equals(path)) {
                security.sendError(ex, HTTP_METHOD_NOT_ALLOWED, "Method not allowed", "/api/model/insights".equals(path) ? "GET" : "POST");
            } else {
                security.sendError(ex, HTTP_NOT_FOUND, "Not found", null);
            }
        } catch (Exception e) { handleApiException(ex, e); }
    }

    void handleAssistantQuery(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            security.sendError(ex, HTTP_METHOD_NOT_ALLOWED, "Method not allowed", "POST");
            return;
        }
        try {
            Map<String, Object> body = readJsonBody(ex);
            String query = WebSecuritySupport.requireBoundedString(ApiResponseMapper.getString(body, "query", null), "query", MAX_QUERY_LEN, true);
            security.sendJson(ex, HTTP_OK, assistantService.answerQuery(query, ApiResponseMapper.getLong(body, "patientId")), true);
        } catch (Exception e) { handleApiException(ex, e); }
    }

    private Patient registerPatient(Map<String, Object> body) {
        String name = WebSecuritySupport.requireBoundedString(ApiResponseMapper.getString(body, "name", null), "name", MAX_NAME_LEN, true);
        int age = ApiResponseMapper.getInt(body, "age", -1);
        if (age < 0 || age > MAX_AGE) throw new ValidationException("Patient age must be between 0 and " + MAX_AGE);
        String contact = WebSecuritySupport.requireBoundedString(ApiResponseMapper.getString(body, "contactNumber", ""), "contactNumber", 30, false);
        return patientService.registerPatient(null, name, Gender.fromCode(ApiResponseMapper.getString(body, "gender", "F")), age, contact,
                ApiResponseMapper.getBool(body, "scholarship", false), ApiResponseMapper.getBool(body, "hipertension", false),
                ApiResponseMapper.getBool(body, "diabetes", false), ApiResponseMapper.getBool(body, "alcoholism", false),
                ApiResponseMapper.getInt(body, "handicap", 0));
    }

    private Doctor registerDoctor(Map<String, Object> body) {
        String name = WebSecuritySupport.requireBoundedString(ApiResponseMapper.getString(body, "name", null), "name", MAX_NAME_LEN, true);
        int age = ApiResponseMapper.getInt(body, "age", 40);
        if (age < MIN_DOCTOR_AGE || age > MAX_AGE) throw new ValidationException("Doctor age must be between 18 and " + MAX_AGE);
        String specialty = WebSecuritySupport.requireBoundedString(ApiResponseMapper.getString(body, "specialty", "General Medicine"), "specialty", MAX_TEXT_LEN, true);
        String room = WebSecuritySupport.requireBoundedString(ApiResponseMapper.getString(body, "roomNumber", "Room 101"), "roomNumber", 40, false);
        String contact = WebSecuritySupport.requireBoundedString(ApiResponseMapper.getString(body, "contactNumber", ""), "contactNumber", 30, false);
        return doctorService.registerDoctor(null, name, Gender.fromCode(ApiResponseMapper.getString(body, "gender", "F")),
                age, specialty, room, contact, ApiResponseMapper.getBool(body, "available", true));
    }

    private Map<String, Object> bookAppointment(Map<String, Object> body) {
        Long patientId = ApiResponseMapper.getLong(body, "patientId");
        Long doctorId = ApiResponseMapper.getLong(body, "doctorId");
        String dateStr = ApiResponseMapper.getString(body, "appointmentDay", null);
        if (patientId == null || doctorId == null || dateStr == null || dateStr.isBlank()) {
            throw new ValidationException("patientId, doctorId, and appointmentDay (YYYY-MM-DD) are required");
        }
        WebSecuritySupport.requirePositiveId(patientId, "patientId");
        WebSecuritySupport.requirePositiveId(doctorId, "doctorId");
        Doctor doctor = doctorService.getDoctorById(doctorId)
                .orElseThrow(() -> new EntityNotFoundException("Doctor with ID " + doctorId + " not found"));
        String hood = WebSecuritySupport.requireBoundedString(ApiResponseMapper.getString(body, "neighbourhood", "CENTRO"), "neighbourhood", MAX_TEXT_LEN, false);
        String notes = WebSecuritySupport.requireBoundedString(ApiResponseMapper.getString(body, "notes", ""), "notes", MAX_NOTES_LEN, false);
        Appointment booked = appointmentService.bookAppointment(null, patientId, doctor, LocalDateTime.now(),
                LocalDate.parse(dateStr.trim()), LocalTime.parse(ApiResponseMapper.getString(body, "appointmentTime", "09:30").trim()), hood, notes);
        reminderService.syncRemindersWithAppointments();
        List<Map<String, Object>> alts = booked.getPredictionResult() != null && booked.getPredictionResult().getRiskCategory() != RiskCategory.LOW
                ? topAlternativeSlots(booked.getPatient(), doctor, hood) : List.of();
        int queueLoad = appointmentService.getTodayQueue(LocalDate.now()).size();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("appointment", ApiResponseMapper.toAppointmentMap(booked));
        resp.put("triageGuidance", ApiResponseMapper.buildTriageGuidance(booked.getPredictionResult(), booked.getLeadDays(), queueLoad));
        resp.put("recommendedAlternativeSlots", alts);
        return resp;
    }

    private Map<String, Object> runPredictionSimulation(Map<String, Object> body) {
        int age = ApiResponseMapper.getInt(body, "age", 35);
        if (age < 0 || age > MAX_AGE) throw new ValidationException("Age must be between 0 and " + MAX_AGE);
        LocalDate apptDate = LocalDate.parse(ApiResponseMapper.getString(body, "appointmentDay", LocalDate.now().plusDays(7).toString()).trim());
        if (apptDate.isBefore(LocalDate.now())) throw new ValidationException("appointmentDay cannot be in the past");
        String hood = WebSecuritySupport.requireBoundedString(ApiResponseMapper.getString(body, "neighbourhood", "CENTRO"), "neighbourhood", MAX_TEXT_LEN, false);
        Patient tempPat = new Patient(0L, "Simulation", Gender.fromCode(ApiResponseMapper.getString(body, "gender", "F")), age);
        Doctor tempDoc = doctorService.getAllDoctors().stream().findFirst().orElse(new Doctor(201L, "Dr. Beatriz Silva", Gender.F, 42, "Cardiology", "Room 201"));
        Appointment tempAppt = new Appointment(0L, tempPat, tempDoc, LocalDateTime.now(), apptDate, hood);
        PredictionResult pred = predictionService.predict(tempAppt);
        int queueLoad = appointmentService.getTodayQueue(LocalDate.now()).size();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("leadDays", tempAppt.getLeadDays());
        resp.put("appointmentWeekday", apptDate.getDayOfWeek().name());
        resp.put("todayQueueLoad", queueLoad);
        resp.put("prediction", ApiResponseMapper.toPredictionMap(pred));
        resp.put("triageGuidance", ApiResponseMapper.buildTriageGuidance(pred, tempAppt.getLeadDays(), queueLoad));
        resp.put("recommendedAlternativeSlots", topAlternativeSlots(tempPat, tempDoc, hood));
        return resp;
    }

    private Appointment applyQueueTransition(String path, Long apptId) {
        switch (path) {
            case "/api/queue/checkin": return appointmentService.checkInPatient(apptId);
            case "/api/queue/start": return appointmentService.startConsultation(apptId);
            case "/api/queue/complete": return appointmentService.completeAppointment(apptId);
            case "/api/queue/noshow": return appointmentService.recordNoShow(apptId);
            case "/api/queue/cancel": return appointmentService.cancelAppointment(apptId);
            default: return null;
        }
    }

    private List<Map<String, Object>> topAlternativeSlots(Patient patient, Doctor doctor, String hood) {
        return appointmentService.findLowerRiskSlots(patient, doctor, LocalDate.now(), SLOT_SCAN_DAYS, hood)
                .stream().limit(MAX_ALT_SLOTS).map(ApiResponseMapper::toSlotMap).toList();
    }

    private static long countByRisk(List<Appointment> all, RiskCategory target) {
        return all.stream().filter(a -> a.getPredictionResult() != null && a.getPredictionResult().getRiskCategory() == target).count();
    }

    private static long countByStatus(List<Appointment> all, AttendanceStatus target) {
        return all.stream().filter(a -> a.getAttendanceStatus() == target).count();
    }

    private Map<String, Object> readJsonBody(HttpExchange ex) throws IOException {
        try (InputStream bodyStream = ex.getRequestBody()) {
            byte[] raw = bodyStream.readNBytes(MAX_BODY_BYTES + 1);
            if (raw.length > MAX_BODY_BYTES) throw new PayloadTooLargeException("Request body exceeds maximum allowed size (64 KB)");
            return JsonUtil.parseObject(new String(raw, StandardCharsets.UTF_8));
        }
    }

    private void handleApiException(HttpExchange ex, Exception e) throws IOException {
        if (e instanceof PayloadTooLargeException) security.sendError(ex, HTTP_PAYLOAD_TOO_LARGE, e.getMessage(), null);
        else if (e instanceof ValidationException || e instanceof IllegalArgumentException || e instanceof DateTimeParseException || e instanceof ClinicFlowException) {
            security.sendError(ex, HTTP_BAD_REQUEST, e.getMessage(), null);
        } else if (e instanceof EntityNotFoundException) security.sendError(ex, HTTP_NOT_FOUND, e.getMessage(), null);
        else if (e instanceof DuplicateEntityException) security.sendError(ex, HTTP_CONFLICT, e.getMessage(), null);
        else {
            LOGGER.log(Level.SEVERE, "Unhandled API error", e);
            security.sendError(ex, HTTP_SERVER_ERROR, "Internal server error", null);
        }
    }

    private static final class PayloadTooLargeException extends RuntimeException {
        PayloadTooLargeException(String message) { super(message); }
    }
}
