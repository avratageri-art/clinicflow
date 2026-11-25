package com.clinicflow.web;

import com.clinicflow.ml.DataPreprocessor;
import com.clinicflow.ml.DataValidator;
import com.clinicflow.ml.ModelEvaluator;
import com.clinicflow.ml.ModelRepository;
import com.clinicflow.ml.NoShowModelTrainer;
import com.clinicflow.model.*;
import com.clinicflow.repository.*;
import com.clinicflow.service.*;
import com.clinicflow.util.ApplicationConfig;
import com.clinicflow.util.ClinicFlowException;
import com.clinicflow.util.DuplicateEntityException;
import com.clinicflow.util.EntityNotFoundException;
import com.clinicflow.util.ValidationException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight, Cloud Run / Render-ready HTTP web server for ClinicFlow.
 * Binds to 0.0.0.0:$PORT (default 8080), auto-trains the compact Weka model on startup
 * if missing, enforces security headers and per-IP rate limiting, and serves the
 * accessible single-page web UI at /.
 */
public class ClinicFlowWebServer {

    private static final Logger LOGGER = Logger.getLogger(ClinicFlowWebServer.class.getName());
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_TEXT_LENGTH = 120;
    private static final int MAX_NOTES_LENGTH = 300;
    private static final int MAX_AGE = 120;
    private static final int EXECUTOR_THREADS = 8;

    private final ApplicationConfig config;
    private final PatientService patientService;
    private final DoctorService doctorService;
    private final AppointmentService appointmentService;
    private final ReminderService reminderService;
    private final ModelInsightsService modelInsightsService;
    private final ClinicalAssistantService assistantService;
    private final WekaNoShowPredictionService predictionService;
    private final NoShowModelTrainer modelTrainer;
    private final WebSecuritySupport security = new WebSecuritySupport();

    private HttpServer server;
    private int boundPort;

    public ClinicFlowWebServer() {
        this(ApplicationConfig.getInstance());
    }

    public ClinicFlowWebServer(ApplicationConfig config) {
        this.config = config;
        PatientRepository patientRepo = new CsvPatientRepository(config.getAppDataPath().resolve("patients.csv").toFile());
        DoctorRepository doctorRepo = new CsvDoctorRepository(config.getAppDataPath().resolve("doctors.csv").toFile());
        AppointmentRepository apptRepo = new CsvAppointmentRepository(config.getAppDataPath().resolve("appointments.csv").toFile(), patientRepo, doctorRepo);
        ReminderRepository reminderRepo = new CsvReminderRepository(config.getAppDataPath().resolve("reminders.csv").toFile());

        this.predictionService = new WekaNoShowPredictionService(config);
        this.patientService = new PatientService(patientRepo, apptRepo);
        this.doctorService = new DoctorService(doctorRepo, apptRepo);
        this.appointmentService = new AppointmentService(patientRepo, apptRepo, doctorRepo, new DataValidator(), predictionService);
        this.reminderService = new ReminderService(reminderRepo, apptRepo);
        this.modelInsightsService = new ModelInsightsService(config);
        this.assistantService = new ClinicalAssistantService(patientService, doctorService, appointmentService, reminderService, predictionService);
        this.modelTrainer = new NoShowModelTrainer(new DataPreprocessor(), new ModelEvaluator(), new ModelRepository(), config);

        seedInitialDataIfNeeded();
    }

    private void seedInitialDataIfNeeded() {
        if (doctorService.getAllDoctors().isEmpty()) {
            doctorService.registerDoctor(201L, "Dr. Beatriz Silva", Gender.F, 42, "Cardiology", "Room 201", "+55-27-98111-2233", true);
            doctorService.registerDoctor(202L, "Dr. Ricardo Mendes", Gender.M, 48, "General Medicine", "Room 105", "+55-27-98222-3344", true);
            doctorService.registerDoctor(203L, "Dr. Juliana Costa", Gender.F, 36, "Pediatrics", "Room 302", "+55-27-98333-4455", true);
        }
        reminderService.syncRemindersWithAppointments();
    }

    private void ensureModelLoaded() {
        if (!predictionService.isModelAvailable()) {
            LOGGER.info("No serialized Weka model found; training compact model on startup...");
            try {
                modelTrainer.runPipeline();
                predictionService.reloadModel();
                LOGGER.info("Startup Weka model training complete. Model available: " + predictionService.isModelAvailable());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Startup model training failed; service will run in degraded mode", e);
            }
        }
    }

    /**
     * Starts the HTTP server on 0.0.0.0:{@code port} and ensures the Weka ML model is loaded.
     */
    public void start(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        this.boundPort = server.getAddress().getPort();
        server.setExecutor(Executors.newFixedThreadPool(EXECUTOR_THREADS));

        server.createContext("/", this::handleRoot);
        server.createContext("/health", this::handleHealth);
        server.createContext("/api/dashboard", wrapApi(this::handleDashboard));
        server.createContext("/api/patients", wrapApi(this::handlePatients));
        server.createContext("/api/doctors", wrapApi(this::handleDoctors));
        server.createContext("/api/appointments", wrapApi(this::handleAppointments));
        server.createContext("/api/predict", wrapApi(this::handlePredict));
        server.createContext("/api/queue", wrapApi(this::handleQueue));
        server.createContext("/api/reminders", wrapApi(this::handleReminders));
        server.createContext("/api/model", wrapApi(this::handleModel));
        server.createContext("/api/assistant/query", wrapApi(this::handleAssistantQuery));

        server.start();
        LOGGER.info("ClinicFlow web server listening on http://0.0.0.0:" + boundPort);
        ensureModelLoaded();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public int getPort() {
        return boundPort;
    }

    public static int resolvePort(String[] args) {
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                int p = Integer.parseInt(envPort.trim());
                if (p > 0 && p <= 65535) {
                    return p;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return 8080;
    }

    private HttpHandler wrapApi(HttpHandler delegate) {
        return ex -> {
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                security.applySecurityHeaders(ex, true);
                ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                ex.sendResponseHeaders(204, -1);
                ex.close();
                return;
            }
            if (!security.allowRequest(ex)) {
                security.sendError(ex, 429, "Rate limit exceeded (max 120 requests/minute)", null);
                return;
            }
            delegate.handle(ex);
        };
    }

    private void handleRoot(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            security.sendError(ex, 405, "Method not allowed", "GET");
            return;
        }
        String path = ex.getRequestURI().getPath();
        if (!"/".equals(path) && !"/index.html".equals(path)) {
            security.sendError(ex, 404, "Not found", null);
            return;
        }
        try (InputStream is = getClass().getResourceAsStream("/web/index.html")) {
            if (is == null) {
                sendText(ex, 500, "text/plain; charset=utf-8", "Web UI resource missing");
                return;
            }
            byte[] html = is.readAllBytes();
            security.applySecurityHeaders(ex, false);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'");
            ex.sendResponseHeaders(200, html.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(html);
            }
        }
    }

    private void handleHealth(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            security.sendError(ex, 405, "Method not allowed", "GET");
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "UP");
        payload.put("service", "ClinicFlow");
        payload.put("modelAvailable", predictionService.isModelAvailable());
        payload.put("timestamp", LocalDateTime.now().toString());
        security.sendJson(ex, 200, payload, true);
    }

    private void handleDashboard(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            security.sendError(ex, 405, "Method not allowed", "GET");
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
        security.sendJson(ex, 200, data, true);
    }

    private void handlePatients(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        try {
            if ("GET".equalsIgnoreCase(method)) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (Patient p : patientService.getAllPatients()) {
                    list.add(toPatientMap(p));
                }
                security.sendJson(ex, 200, Map.of("patients", list), true);
            } else if ("POST".equalsIgnoreCase(method)) {
                Patient saved = registerPatientFromBody(readJsonBody(ex));
                security.sendJson(ex, 201, Map.of("patient", toPatientMap(saved)), true);
            } else {
                security.sendError(ex, 405, "Method not allowed", "GET, POST");
            }
        } catch (Exception e) {
            handleApiException(ex, e);
        }
    }

    private Patient registerPatientFromBody(Map<String, Object> body) {
        String name = WebSecuritySupport.requireBoundedString(getString(body, "name"), "name", MAX_NAME_LENGTH, true);
        Gender gender = Gender.fromCode(getString(body, "gender", "F"));
        int age = getInt(body, "age", -1);
        if (age < 0 || age > MAX_AGE) {
            throw new ValidationException("Patient age must be between 0 and " + MAX_AGE);
        }
        String contact = WebSecuritySupport.requireBoundedString(getString(body, "contactNumber", ""), "contactNumber", 30, false);
        return patientService.registerPatient(null, name, gender, age, contact,
                getBool(body, "scholarship", false),
                getBool(body, "hipertension", false),
                getBool(body, "diabetes", false),
                getBool(body, "alcoholism", false),
                getInt(body, "handicap", 0));
    }

    private void handleDoctors(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        try {
            if ("GET".equalsIgnoreCase(method)) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (Doctor d : doctorService.getAllDoctors()) {
                    list.add(toDoctorMap(d));
                }
                security.sendJson(ex, 200, Map.of("doctors", list), true);
            } else if ("POST".equalsIgnoreCase(method)) {
                Doctor saved = registerDoctorFromBody(readJsonBody(ex));
                security.sendJson(ex, 201, Map.of("doctor", toDoctorMap(saved)), true);
            } else {
                security.sendError(ex, 405, "Method not allowed", "GET, POST");
            }
        } catch (Exception e) {
            handleApiException(ex, e);
        }
    }

    private Doctor registerDoctorFromBody(Map<String, Object> body) {
        String name = WebSecuritySupport.requireBoundedString(getString(body, "name"), "name", MAX_NAME_LENGTH, true);
        Gender gender = Gender.fromCode(getString(body, "gender", "F"));
        int age = getInt(body, "age", 40);
        if (age < 18 || age > MAX_AGE) {
            throw new ValidationException("Doctor age must be between 18 and " + MAX_AGE);
        }
        String specialty = WebSecuritySupport.requireBoundedString(getString(body, "specialty", "General Medicine"), "specialty", MAX_TEXT_LENGTH, true);
        String roomNumber = WebSecuritySupport.requireBoundedString(getString(body, "roomNumber", "Room 101"), "roomNumber", 40, false);
        String contactNumber = WebSecuritySupport.requireBoundedString(getString(body, "contactNumber", ""), "contactNumber", 30, false);
        return doctorService.registerDoctor(null, name, gender, age, specialty, roomNumber, contactNumber, getBool(body, "available", true));
    }

    private void handleAppointments(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        try {
            if ("GET".equalsIgnoreCase(method)) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (Appointment a : appointmentService.getAllAppointments()) {
                    list.add(toAppointmentMap(a));
                }
                security.sendJson(ex, 200, Map.of("appointments", list), true);
            } else if ("POST".equalsIgnoreCase(method)) {
                security.sendJson(ex, 201, bookAppointmentFromBody(readJsonBody(ex)), true);
            } else {
                security.sendError(ex, 405, "Method not allowed", "GET, POST");
            }
        } catch (Exception e) {
            handleApiException(ex, e);
        }
    }

    private Map<String, Object> bookAppointmentFromBody(Map<String, Object> body) {
        Long patientId = getLong(body, "patientId");
        Long doctorId = getLong(body, "doctorId");
        String dateStr = getString(body, "appointmentDay");
        String timeStr = getString(body, "appointmentTime", "09:30");
        String neighbourhood = WebSecuritySupport.requireBoundedString(getString(body, "neighbourhood", "CENTRO"), "neighbourhood", MAX_TEXT_LENGTH, false);
        String notes = WebSecuritySupport.requireBoundedString(getString(body, "notes", ""), "notes", MAX_NOTES_LENGTH, false);
        if (patientId == null || doctorId == null || dateStr == null || dateStr.isBlank()) {
            throw new ValidationException("patientId, doctorId, and appointmentDay (YYYY-MM-DD) are required");
        }
        Doctor doctor = doctorService.getDoctorById(doctorId)
                .orElseThrow(() -> new EntityNotFoundException("Doctor with ID " + doctorId + " not found"));
        Appointment booked = appointmentService.bookAppointment(null, patientId, doctor,
                LocalDateTime.now(), LocalDate.parse(dateStr.trim()), LocalTime.parse(timeStr.trim()), neighbourhood, notes);
        reminderService.syncRemindersWithAppointments();

        List<Map<String, Object>> alternatives = booked.getPredictionResult() != null
                && booked.getPredictionResult().getRiskCategory() != RiskCategory.LOW
                ? topAlternativeSlots(booked.getPatient(), doctor, neighbourhood)
                : List.of();
        int queueLoad = appointmentService.getTodayQueue(LocalDate.now()).size();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("appointment", toAppointmentMap(booked));
        resp.put("triageGuidance", buildTriageGuidance(booked.getPredictionResult(), booked.getLeadDays(), queueLoad));
        resp.put("recommendedAlternativeSlots", alternatives);
        return resp;
    }

    private void handlePredict(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            security.sendError(ex, 405, "Method not allowed", "POST");
            return;
        }
        try {
            Map<String, Object> body = readJsonBody(ex);
            int age = getInt(body, "age", 35);
            if (age < 0 || age > MAX_AGE) {
                throw new ValidationException("Age must be between 0 and " + MAX_AGE);
            }
            Gender gender = Gender.fromCode(getString(body, "gender", "F"));
            LocalDate apptDate = LocalDate.parse(getString(body, "appointmentDay", LocalDate.now().plusDays(7).toString()).trim());
            if (apptDate.isBefore(LocalDate.now())) {
                throw new ValidationException("appointmentDay cannot be in the past");
            }
            String neighbourhood = WebSecuritySupport.requireBoundedString(getString(body, "neighbourhood", "CENTRO"), "neighbourhood", MAX_TEXT_LENGTH, false);
            Patient tempPatient = new Patient(0L, "Simulation", gender, age);
            Doctor tempDoctor = doctorService.getAllDoctors().stream().findFirst()
                    .orElse(new Doctor(201L, "Dr. Beatriz Silva", Gender.F, 42, "Cardiology", "Room 201"));
            Appointment tempAppt = new Appointment(0L, tempPatient, tempDoctor, LocalDateTime.now(), apptDate, neighbourhood);
            PredictionResult pred = predictionService.predict(tempAppt);

            int queueLoad = appointmentService.getTodayQueue(LocalDate.now()).size();
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("leadDays", tempAppt.getLeadDays());
            resp.put("appointmentWeekday", apptDate.getDayOfWeek().name());
            resp.put("todayQueueLoad", queueLoad);
            resp.put("prediction", toPredictionMap(pred));
            resp.put("triageGuidance", buildTriageGuidance(pred, tempAppt.getLeadDays(), queueLoad));
            resp.put("recommendedAlternativeSlots", topAlternativeSlots(tempPatient, tempDoctor, neighbourhood));
            security.sendJson(ex, 200, resp, true);
        } catch (Exception e) {
            handleApiException(ex, e);
        }
    }

    private void handleQueue(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        try {
            if ("GET".equalsIgnoreCase(method) && "/api/queue".equals(path)) {
                List<Map<String, Object>> queueList = new ArrayList<>();
                for (Appointment a : appointmentService.getTodayQueue(LocalDate.now())) {
                    queueList.add(toAppointmentMap(a));
                }
                security.sendJson(ex, 200, Map.of("date", LocalDate.now().toString(), "queue", queueList), true);
                return;
            }
            if (!"POST".equalsIgnoreCase(method)) {
                security.sendError(ex, 405, "Method not allowed", "/api/queue".equals(path) ? "GET" : "POST");
                return;
            }
            Long apptId = getLong(readJsonBody(ex), "appointmentId");
            if (apptId == null) {
                throw new ValidationException("appointmentId is required");
            }
            Appointment updated = applyQueueTransition(path, apptId);
            if (updated == null) {
                security.sendError(ex, 404, "Unknown queue action", null);
                return;
            }
            security.sendJson(ex, 200, Map.of("appointment", toAppointmentMap(updated)), true);
        } catch (Exception e) {
            handleApiException(ex, e);
        }
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

    private void handleReminders(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        try {
            if ("GET".equalsIgnoreCase(method) && "/api/reminders".equals(path)) {
                reminderService.syncRemindersWithAppointments();
                List<Map<String, Object>> list = new ArrayList<>();
                for (ReminderRecord r : reminderService.getAllReminders()) {
                    list.add(toReminderMap(r));
                }
                security.sendJson(ex, 200, Map.of("reminders", list, "disclaimer", ReminderService.ACADEMIC_DISCLAIMER), true);
                return;
            }
            if ("POST".equalsIgnoreCase(method) && "/api/reminders/send".equals(path)) {
                Long reminderId = getLong(readJsonBody(ex), "reminderId");
                if (reminderId == null) {
                    throw new ValidationException("reminderId is required");
                }
                ReminderRecord sent = reminderService.simulateSendReminder(reminderId);
                security.sendJson(ex, 200, Map.of("reminderId", sent.getReminderId(), "status", sent.getStatus()), true);
                return;
            }
            security.sendError(ex, 405, "Method not allowed", "/api/reminders".equals(path) ? "GET" : "POST");
        } catch (Exception e) {
            handleApiException(ex, e);
        }
    }

    private void handleModel(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        try {
            if ("GET".equalsIgnoreCase(method) && "/api/model/insights".equals(path)) {
                security.sendJson(ex, 200, toModelInsightsMap(modelInsightsService.getModelSpecification()), true);
                return;
            }
            if ("POST".equalsIgnoreCase(method) && "/api/model/retrain".equals(path)) {
                NoShowModelTrainer.TrainingPipelineResult res = modelTrainer.runPipeline();
                predictionService.reloadModel();
                security.sendJson(ex, 200, Map.of(
                        "status", "RETRAINED",
                        "selectedModel", res.getSelectedResult().getModelName(),
                        "accuracy", res.getSelectedResult().getAccuracy(),
                        "rocArea", res.getSelectedResult().getRocArea()
                ), true);
                return;
            }
            if ("/api/model/insights".equals(path) || "/api/model/retrain".equals(path)) {
                security.sendError(ex, 405, "Method not allowed", "/api/model/insights".equals(path) ? "GET" : "POST");
            } else {
                security.sendError(ex, 404, "Not found", null);
            }
        } catch (Exception e) {
            handleApiException(ex, e);
        }
    }

    private void handleAssistantQuery(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            security.sendError(ex, 405, "Method not allowed", "POST");
            return;
        }
        try {
            Map<String, Object> body = readJsonBody(ex);
            String query = WebSecuritySupport.requireBoundedString(getString(body, "query"), "query", MAX_NOTES_LENGTH, true);
            Long patientId = getLong(body, "patientId");
            security.sendJson(ex, 200, assistantService.answerQuery(query, patientId), true);
        } catch (Exception e) {
            handleApiException(ex, e);
        }
    }

    private List<Map<String, Object>> topAlternativeSlots(Patient patient, Doctor doctor, String neighbourhood) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AppointmentService.LowerRiskSlot slot : appointmentService.findLowerRiskSlots(patient, doctor, LocalDate.now(), 10, neighbourhood)) {
            if (out.size() >= 3) break;
            out.add(toSlotMap(slot));
        }
        return out;
    }

    private static long countByRisk(List<Appointment> all, RiskCategory target) {
        return all.stream().filter(a -> a.getPredictionResult() != null && a.getPredictionResult().getRiskCategory() == target).count();
    }

    private static long countByStatus(List<Appointment> all, AttendanceStatus target) {
        return all.stream().filter(a -> a.getAttendanceStatus() == target).count();
    }

    private Map<String, Object> toPatientMap(Patient p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("gender", p.getGender().name());
        m.put("age", p.getAge());
        m.put("contactNumber", p.getContactNumber());
        m.put("scholarship", p.isScholarship());
        m.put("hipertension", p.isHipertension());
        m.put("diabetes", p.isDiabetes());
        return m;
    }

    private Map<String, Object> toDoctorMap(Doctor d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("name", d.getName());
        m.put("gender", d.getGender().name());
        m.put("age", d.getAge());
        m.put("specialty", d.getSpecialty());
        m.put("roomNumber", d.getRoomNumber());
        m.put("contactNumber", d.getContactNumber());
        m.put("available", d.isAvailable());
        return m;
    }

    private Map<String, Object> toAppointmentMap(Appointment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("appointmentId", a.getAppointmentId());
        m.put("queueToken", a.getQueueToken());
        m.put("patientId", a.getPatient() != null ? a.getPatient().getId() : null);
        m.put("patientName", a.getPatient() != null ? a.getPatient().getName() : "Unknown");
        m.put("doctorId", a.getDoctor() != null ? a.getDoctor().getId() : null);
        m.put("doctorName", a.getDoctor() != null ? a.getDoctor().getName() : "Unknown");
        m.put("specialty", a.getDoctor() != null ? a.getDoctor().getSpecialty() : "");
        m.put("appointmentDay", a.getAppointmentDay().toString());
        m.put("appointmentTime", a.getAppointmentTime() != null ? a.getAppointmentTime().toString() : "09:00");
        m.put("leadDays", a.getLeadDays());
        m.put("neighbourhood", a.getNeighbourhood());
        m.put("attendanceStatus", a.getAttendanceStatus().name());
        m.put("reminderStatus", a.getReminderStatus());
        m.put("prediction", toPredictionMap(a.getPredictionResult()));
        return m;
    }

    private Map<String, Object> toPredictionMap(PredictionResult pred) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (pred == null) {
            m.put("modelAvailable", false);
            return m;
        }
        m.put("modelAvailable", pred.isModelAvailable());
        m.put("predictedClass", pred.getPredictedClass());
        m.put("noShowProbability", pred.getNoShowProbability());
        m.put("attendanceProbability", pred.getAttendanceProbability());
        m.put("riskCategory", pred.getRiskCategory() != null ? pred.getRiskCategory().name() : null);
        m.put("suggestedAction", pred.getSuggestedAction() != null ? pred.getSuggestedAction().name() : null);
        m.put("recommendedAction", pred.getSuggestedAction() != null ? pred.getSuggestedAction().name() : null);
        m.put("actionDescription", pred.getSuggestedAction() != null ? pred.getSuggestedAction().getActionDescription() : pred.getStatusMessage());
        return m;
    }

    private Map<String, Object> toReminderMap(ReminderRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reminderId", r.getReminderId());
        m.put("appointmentId", r.getAppointmentId());
        m.put("patientName", r.getPatientName());
        m.put("contactNumber", r.getPatientContact());
        m.put("appointmentDay", r.getAppointmentDate().toString());
        m.put("riskCategory", r.getRiskCategory().name());
        m.put("recommendedAction", r.getRecommendedAction().name());
        m.put("actionDescription", r.getRecommendedAction().getActionDescription());
        m.put("status", r.getStatus());
        m.put("sentTimestamp", r.getSentTimestamp() != null ? r.getSentTimestamp().toString() : null);
        return m;
    }

    private Map<String, Object> toModelInsightsMap(ModelInsightsService.ModelSpec spec) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("modelAvailable", predictionService.isModelAvailable());
        m.put("algorithm", spec.algorithm);
        m.put("version", spec.modelVersion);
        m.put("selectedVariant", spec.modelName);
        m.put("trainedAt", spec.trainedAt);
        m.put("datasetSource", spec.datasetName);
        m.put("totalCleanedRecords", spec.cleanRecords);
        m.put("trainRecords", spec.trainCount);
        m.put("testRecords", spec.testCount);
        m.put("numTrees", spec.numTrees);
        m.put("features", spec.features);
        m.put("accuracy", spec.accuracy);
        m.put("precision", spec.precision);
        m.put("recall", spec.recall);
        m.put("f1Score", spec.f1);
        m.put("rocArea", spec.rocArea);
        m.put("confusionMatrix", Map.of("tp", spec.truePositives, "fp", spec.falsePositives,
                "tn", spec.trueNegatives, "fn", spec.falseNegatives));
        return m;
    }

    private Map<String, Object> toSlotMap(AppointmentService.LowerRiskSlot slot) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", slot.getDate().toString());
        m.put("time", slot.getTime().toString());
        m.put("leadDays", slot.getLeadDays());
        m.put("noShowProbability", slot.getNoShowProbability());
        m.put("riskCategory", slot.getRiskCategory().name());
        m.put("suggestedAction", slot.getSuggestedAction().name());
        return m;
    }

    private Map<String, Object> readJsonBody(HttpExchange ex) throws IOException {
        try (InputStream bodyStream = ex.getRequestBody()) {
            byte[] raw = bodyStream.readNBytes(MAX_BODY_BYTES + 1);
            if (raw.length > MAX_BODY_BYTES) {
                throw new PayloadTooLargeException("Request body exceeds maximum allowed size (64 KB)");
            }
            return JsonUtil.parseObject(new String(raw, StandardCharsets.UTF_8));
        }
    }

    private static String buildTriageGuidance(PredictionResult pred, long leadDays, int todayQueueLoad) {
        if (pred == null || !pred.isModelAvailable()) {
            return "Advisory only: ML model unavailable; apply standard clinic confirmation.";
        }
        RiskCategory risk = pred.getRiskCategory();
        String leadContext = leadDays == 0 ? "Same-day booking" : leadDays + "-day lead time";
        String queueContext = todayQueueLoad >= 10 ? "High today queue load (" + todayQueueLoad + " active)" : "Normal today queue load (" + todayQueueLoad + " active)";
        if (risk == RiskCategory.HIGH) {
            return leadContext + " | " + queueContext + " — Prioritize coordinator phone call and prepare optional backfill slot (advisory only; queue order remains strict FIFO).";
        }
        if (risk == RiskCategory.MEDIUM) {
            return leadContext + " | " + queueContext + " — Send two-way SMS confirmation link and offer lower-risk alternative dates if helpful (advisory only).";
        }
        return leadContext + " | " + queueContext + " — Standard automated 24h SMS reminder is sufficient (advisory only).";
    }

    private void handleApiException(HttpExchange ex, Exception e) throws IOException {
        if (e instanceof PayloadTooLargeException) {
            security.sendError(ex, 413, e.getMessage(), null);
        } else if (e instanceof ValidationException || e instanceof IllegalArgumentException || e instanceof DateTimeParseException) {
            security.sendError(ex, 400, e.getMessage(), null);
        } else if (e instanceof EntityNotFoundException) {
            security.sendError(ex, 404, e.getMessage(), null);
        } else if (e instanceof DuplicateEntityException) {
            security.sendError(ex, 409, e.getMessage(), null);
        } else if (e instanceof ClinicFlowException) {
            security.sendError(ex, 400, e.getMessage(), null);
        } else {
            LOGGER.log(Level.SEVERE, "Unhandled API error", e);
            security.sendError(ex, 500, "Internal server error", null);
        }
    }

    private void sendText(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        security.applySecurityHeaders(ex, false);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String getString(Map<String, Object> map, String key) {
        return getString(map, key, null);
    }

    private static String getString(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : defaultVal;
    }

    private static int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val != null) {
            try { return Integer.parseInt(val.toString().trim()); } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    private static Long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).longValue();
        if (val != null && !val.toString().isBlank()) {
            try { return Long.parseLong(val.toString().trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static boolean getBool(Map<String, Object> map, String key, boolean defaultVal) {
        Object val = map.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        if (val != null) return Boolean.parseBoolean(val.toString());
        return defaultVal;
    }

    private static final class PayloadTooLargeException extends RuntimeException {
        PayloadTooLargeException(String message) {
            super(message);
        }
    }
}
