package com.clinicflow.web;

import com.clinicflow.ml.*;
import com.clinicflow.model.*;
import com.clinicflow.repository.*;
import com.clinicflow.service.*;
import com.clinicflow.util.*;
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
 * Focuses strictly on server lifecycle, route dispatch, and HTTP transport.
 */
public class ClinicFlowWebServer {

    private static final Logger LOGGER = Logger.getLogger(ClinicFlowWebServer.class.getName());
    private static final String BIND_HOST = "0.0.0.0";
    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_PORT = 65535;
    private static final int EXECUTOR_THREADS = 8;
    private static final int HTTP_NO_CONTENT = 204;
    private static final int HTTP_UNSUPPORTED_MEDIA = 415;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final String CSP_HEADER =
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final int MAX_NAME_LEN = 120;
    private static final int MAX_TEXT_LEN = 120;
    private static final int MAX_NOTES_LEN = 300;
    private static final int MAX_AGE = 120;
    private static final int MIN_DOCTOR_AGE = 18;
    private static final int MAX_ALT_SLOTS = 3;
    private static final int SLOT_SCAN_DAYS = 10;

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

    /** Creates a web server using the default {@link ApplicationConfig}. */
    public ClinicFlowWebServer() {
        this(ApplicationConfig.getInstance());
    }

    /**
     * Creates a web server wired to the supplied {@link ApplicationConfig}.
     *
     * @param config application configuration (must not be null)
     */
    public ClinicFlowWebServer(ApplicationConfig config) {
        Objects.requireNonNull(config, "ApplicationConfig cannot be null");
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

    /**
     * Starts the HTTP server on {@code 0.0.0.0:port} and initializes the Weka model.
     *
     * @param port TCP port to bind (0 for ephemeral test port)
     * @throws IOException if the socket cannot bind
     */
    public void start(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(BIND_HOST, port), 0);
        this.boundPort = server.getAddress().getPort();
        server.setExecutor(Executors.newFixedThreadPool(EXECUTOR_THREADS));
        registerRoutes();
        server.start();
        LOGGER.info("ClinicFlow web server listening on http://" + BIND_HOST + ":" + boundPort);
        ensureModelLoaded();
    }

    private void registerRoutes() {
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
    }

    private void ensureModelLoaded() {
        if (!predictionService.isModelAvailable()) {
            try {
                modelTrainer.runPipeline();
                predictionService.reloadModel();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Startup model training failed; running in degraded mode", e);
            }
        }
    }

    /** Stops the HTTP server immediately. */
    public void stop() {
        if (server != null) server.stop(0);
    }

    /** @return the bound TCP port */
    public int getPort() {
        return boundPort;
    }

    /**
     * Resolves the listening port from {@code PORT} environment variable or defaults to 8080.
     *
     * @param args CLI arguments
     * @return valid TCP port
     */
    public static int resolvePort(String[] args) {
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                int p = Integer.parseInt(envPort.trim());
                if (p > 0 && p <= MAX_PORT) return p;
            } catch (NumberFormatException ignored) {}
        }
        return DEFAULT_PORT;
    }

    private HttpHandler wrapApi(HttpHandler delegate) {
        return ex -> {
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                security.applySecurityHeaders(ex, true);
                ex.sendResponseHeaders(HTTP_NO_CONTENT, -1);
                ex.close();
                return;
            }
            if (!security.allowRequest(ex)) {
                security.sendError(ex, HTTP_TOO_MANY_REQUESTS, "Rate limit exceeded (max 120 requests/minute)", null);
                return;
            }
            if (!security.hasValidJsonContentType(ex)) {
                security.sendError(ex, HTTP_UNSUPPORTED_MEDIA, "Content-Type must be application/json", null);
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
                security.sendError(ex, 500, "Web UI resource missing", null);
                return;
            }
            byte[] html = is.readAllBytes();
            security.applySecurityHeaders(ex, false);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.getResponseHeaders().set("Content-Security-Policy", CSP_HEADER);
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
        data.put("highRiskCount", all.stream().filter(a -> a.getPredictionResult() != null && a.getPredictionResult().getRiskCategory() == RiskCategory.HIGH).count());
        data.put("mediumRiskCount", all.stream().filter(a -> a.getPredictionResult() != null && a.getPredictionResult().getRiskCategory() == RiskCategory.MEDIUM).count());
        data.put("lowRiskCount", all.stream().filter(a -> a.getPredictionResult() != null && a.getPredictionResult().getRiskCategory() == RiskCategory.LOW).count());
        data.put("attendedCount", all.stream().filter(a -> a.getAttendanceStatus() == AttendanceStatus.ATTENDED).count());
        data.put("noShowCount", all.stream().filter(a -> a.getAttendanceStatus() == AttendanceStatus.NO_SHOW).count());
        data.put("pendingReminders", reminderService.getPendingReminders().size());
        data.put("modelAvailable", predictionService.isModelAvailable());
        security.sendJson(ex, 200, data, true);
    }

    private void handlePatients(HttpExchange ex) throws IOException {
        try {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                List<Map<String, Object>> list = patientService.getAllPatients().stream().map(ApiResponseMapper::toPatientMap).toList();
                security.sendJson(ex, 200, Map.of("patients", list), true);
            } else if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                security.sendJson(ex, 201, Map.of("patient", ApiResponseMapper.toPatientMap(registerPatient(readJsonBody(ex)))), true);
            } else {
                security.sendError(ex, 405, "Method not allowed", "GET, POST");
            }
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

    private void handleDoctors(HttpExchange ex) throws IOException {
        try {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                List<Map<String, Object>> list = doctorService.getAllDoctors().stream().map(ApiResponseMapper::toDoctorMap).toList();
                security.sendJson(ex, 200, Map.of("doctors", list), true);
            } else if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                security.sendJson(ex, 201, Map.of("doctor", ApiResponseMapper.toDoctorMap(registerDoctor(readJsonBody(ex)))), true);
            } else {
                security.sendError(ex, 405, "Method not allowed", "GET, POST");
            }
        } catch (Exception e) { handleApiException(ex, e); }
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

    private void handleAppointments(HttpExchange ex) throws IOException {
        try {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                List<Map<String, Object>> list = appointmentService.getAllAppointments().stream().map(ApiResponseMapper::toAppointmentMap).toList();
                security.sendJson(ex, 200, Map.of("appointments", list), true);
            } else if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                security.sendJson(ex, 201, bookAppointment(readJsonBody(ex)), true);
            } else {
                security.sendError(ex, 405, "Method not allowed", "GET, POST");
            }
        } catch (Exception e) { handleApiException(ex, e); }
    }

    private Map<String, Object> bookAppointment(Map<String, Object> body) {
        Long patientId = ApiResponseMapper.getLong(body, "patientId");
        Long doctorId = ApiResponseMapper.getLong(body, "doctorId");
        String dateStr = ApiResponseMapper.getString(body, "appointmentDay", null);
        if (patientId == null || doctorId == null || dateStr == null || dateStr.isBlank()) {
            throw new ValidationException("patientId, doctorId, and appointmentDay (YYYY-MM-DD) are required");
        }
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

    private void handlePredict(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            security.sendError(ex, 405, "Method not allowed", "POST");
            return;
        }
        try {
            Map<String, Object> body = readJsonBody(ex);
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
            security.sendJson(ex, 200, resp, true);
        } catch (Exception e) { handleApiException(ex, e); }
    }

    private void handleQueue(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod()) && "/api/queue".equals(path)) {
                List<Map<String, Object>> q = appointmentService.getTodayQueue(LocalDate.now()).stream().map(ApiResponseMapper::toAppointmentMap).toList();
                security.sendJson(ex, 200, Map.of("date", LocalDate.now().toString(), "queue", q), true);
            } else if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                Long apptId = ApiResponseMapper.getLong(readJsonBody(ex), "appointmentId");
                if (apptId == null) throw new ValidationException("appointmentId is required");
                Appointment updated = applyQueueTransition(path, apptId);
                if (updated == null) security.sendError(ex, 404, "Unknown queue action", null);
                else security.sendJson(ex, 200, Map.of("appointment", ApiResponseMapper.toAppointmentMap(updated)), true);
            } else {
                security.sendError(ex, 405, "Method not allowed", "/api/queue".equals(path) ? "GET" : "POST");
            }
        } catch (Exception e) { handleApiException(ex, e); }
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
        try {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod()) && "/api/reminders".equals(path)) {
                reminderService.syncRemindersWithAppointments();
                List<Map<String, Object>> list = reminderService.getAllReminders().stream().map(ApiResponseMapper::toReminderMap).toList();
                security.sendJson(ex, 200, Map.of("reminders", list, "disclaimer", ReminderService.ACADEMIC_DISCLAIMER), true);
            } else if ("POST".equalsIgnoreCase(ex.getRequestMethod()) && "/api/reminders/send".equals(path)) {
                Long id = ApiResponseMapper.getLong(readJsonBody(ex), "reminderId");
                if (id == null) throw new ValidationException("reminderId is required");
                ReminderRecord sent = reminderService.simulateSendReminder(id);
                security.sendJson(ex, 200, Map.of("reminderId", sent.getReminderId(), "status", sent.getStatus()), true);
            } else {
                security.sendError(ex, 405, "Method not allowed", "/api/reminders".equals(path) ? "GET" : "POST");
            }
        } catch (Exception e) { handleApiException(ex, e); }
    }

    private void handleModel(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod()) && "/api/model/insights".equals(path)) {
                security.sendJson(ex, 200, ApiResponseMapper.toModelInsightsMap(modelInsightsService.getModelSpecification(), predictionService.isModelAvailable()), true);
            } else if ("POST".equalsIgnoreCase(ex.getRequestMethod()) && "/api/model/retrain".equals(path)) {
                NoShowModelTrainer.TrainingPipelineResult res = modelTrainer.runPipeline();
                predictionService.reloadModel();
                security.sendJson(ex, 200, Map.of("status", "RETRAINED", "selectedModel", res.getSelectedResult().getModelName(),
                        "accuracy", res.getSelectedResult().getAccuracy(), "rocArea", res.getSelectedResult().getRocArea()), true);
            } else if ("/api/model/insights".equals(path) || "/api/model/retrain".equals(path)) {
                security.sendError(ex, 405, "Method not allowed", "/api/model/insights".equals(path) ? "GET" : "POST");
            } else {
                security.sendError(ex, 404, "Not found", null);
            }
        } catch (Exception e) { handleApiException(ex, e); }
    }

    private void handleAssistantQuery(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            security.sendError(ex, 405, "Method not allowed", "POST");
            return;
        }
        try {
            Map<String, Object> body = readJsonBody(ex);
            String query = WebSecuritySupport.requireBoundedString(ApiResponseMapper.getString(body, "query", null), "query", MAX_NOTES_LEN, true);
            security.sendJson(ex, 200, assistantService.answerQuery(query, ApiResponseMapper.getLong(body, "patientId")), true);
        } catch (Exception e) { handleApiException(ex, e); }
    }

    private List<Map<String, Object>> topAlternativeSlots(Patient patient, Doctor doctor, String hood) {
        return appointmentService.findLowerRiskSlots(patient, doctor, LocalDate.now(), SLOT_SCAN_DAYS, hood)
                .stream().limit(MAX_ALT_SLOTS).map(ApiResponseMapper::toSlotMap).toList();
    }

    private Map<String, Object> readJsonBody(HttpExchange ex) throws IOException {
        try (InputStream bodyStream = ex.getRequestBody()) {
            byte[] raw = bodyStream.readNBytes(MAX_BODY_BYTES + 1);
            if (raw.length > MAX_BODY_BYTES) throw new PayloadTooLargeException("Request body exceeds maximum allowed size (64 KB)");
            return JsonUtil.parseObject(new String(raw, StandardCharsets.UTF_8));
        }
    }

    private void handleApiException(HttpExchange ex, Exception e) throws IOException {
        if (e instanceof PayloadTooLargeException) security.sendError(ex, 413, e.getMessage(), null);
        else if (e instanceof ValidationException || e instanceof IllegalArgumentException || e instanceof DateTimeParseException || e instanceof ClinicFlowException) {
            security.sendError(ex, 400, e.getMessage(), null);
        } else if (e instanceof EntityNotFoundException) security.sendError(ex, 404, e.getMessage(), null);
        else if (e instanceof DuplicateEntityException) security.sendError(ex, 409, e.getMessage(), null);
        else {
            LOGGER.log(Level.SEVERE, "Unhandled API error", e);
            security.sendError(ex, 500, "Internal server error", null);
        }
    }

    private static final class PayloadTooLargeException extends RuntimeException {
        PayloadTooLargeException(String message) { super(message); }
    }
}
