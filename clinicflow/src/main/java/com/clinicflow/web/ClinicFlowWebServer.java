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
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight, Cloud Run-ready HTTP web server for ClinicFlow.
 * Binds to 0.0.0.0:$PORT (default 8080), auto-trains the compact Weka model on startup
 * if missing, exposes REST JSON endpoints for the smart assistant, and serves an
 * accessible web UI at /.
 */
public class ClinicFlowWebServer {

    private static final Logger LOGGER = Logger.getLogger(ClinicFlowWebServer.class.getName());
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_TEXT_LENGTH = 120;
    private static final int MAX_NOTES_LENGTH = 300;
    private static final int MAX_AGE = 120;

    private final ApplicationConfig config;
    private final PatientService patientService;
    private final DoctorService doctorService;
    private final AppointmentService appointmentService;
    private final ReminderService reminderService;
    private final ModelInsightsService modelInsightsService;
    private final ClinicalAssistantService assistantService;
    private final WekaNoShowPredictionService predictionService;
    private final NoShowModelTrainer modelTrainer;

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
        DataValidator validator = new DataValidator();
        this.patientService = new PatientService(patientRepo, apptRepo);
        this.doctorService = new DoctorService(doctorRepo, apptRepo);
        this.appointmentService = new AppointmentService(patientRepo, apptRepo, doctorRepo, validator, predictionService);
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

    public void start(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        this.boundPort = server.getAddress().getPort();
        server.setExecutor(Executors.newFixedThreadPool(8));

        server.createContext("/", this::handleRoot);
        server.createContext("/health", this::handleHealth);
        server.createContext("/api/dashboard", this::handleDashboard);
        server.createContext("/api/patients", this::handlePatients);
        server.createContext("/api/doctors", this::handleDoctors);
        server.createContext("/api/appointments", this::handleAppointments);
        server.createContext("/api/predict", this::handlePredict);
        server.createContext("/api/queue", this::handleQueue);
        server.createContext("/api/reminders", this::handleReminders);
        server.createContext("/api/model", this::handleModel);
        server.createContext("/api/assistant/query", this::handleAssistantQuery);

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

    private void handleRoot(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        String path = ex.getRequestURI().getPath();
        if (!"/".equals(path) && !"/index.html".equals(path)) {
            sendJson(ex, 404, Map.of("error", "Not found"));
            return;
        }
        try (InputStream is = getClass().getResourceAsStream("/web/index.html")) {
            if (is == null) {
                sendText(ex, 500, "text/plain; charset=utf-8", "Web UI resource missing");
                return;
            }
            byte[] html = is.readAllBytes();
            applySecurityHeaders(ex);
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
            sendJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "UP");
        payload.put("service", "ClinicFlow");
        payload.put("modelAvailable", predictionService.isModelAvailable());
        payload.put("timestamp", LocalDateTime.now().toString());
        sendJson(ex, 200, payload);
    }

    private void handleDashboard(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        List<Appointment> all = appointmentService.getAllAppointments();
        List<Appointment> todayQueue = appointmentService.getTodayQueue(LocalDate.now());
        long highRisk = all.stream().filter(a -> a.getPredictionResult() != null && a.getPredictionResult().getRiskCategory() == RiskCategory.HIGH).count();
        long medRisk = all.stream().filter(a -> a.getPredictionResult() != null && a.getPredictionResult().getRiskCategory() == RiskCategory.MEDIUM).count();
        long lowRisk = all.stream().filter(a -> a.getPredictionResult() != null && a.getPredictionResult().getRiskCategory() == RiskCategory.LOW).count();
        long attended = all.stream().filter(a -> a.getAttendanceStatus() == AttendanceStatus.ATTENDED).count();
        long noShow = all.stream().filter(a -> a.getAttendanceStatus() == AttendanceStatus.NO_SHOW).count();
        long pendingReminders = reminderService.getPendingReminders().size();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalPatients", patientService.getAllPatients().size());
        data.put("totalDoctors", doctorService.getAllDoctors().size());
        data.put("totalAppointments", all.size());
        data.put("todayQueueCount", todayQueue.size());
        data.put("highRiskCount", highRisk);
        data.put("mediumRiskCount", medRisk);
        data.put("lowRiskCount", lowRisk);
        data.put("attendedCount", attended);
        data.put("noShowCount", noShow);
        data.put("pendingReminders", pendingReminders);
        data.put("modelAvailable", predictionService.isModelAvailable());
        sendJson(ex, 200, data);
    }

    private void handlePatients(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        try {
            if ("GET".equalsIgnoreCase(method)) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (Patient p : patientService.getAllPatients()) {
                    list.add(toPatientMap(p));
                }
                sendJson(ex, 200, Map.of("patients", list));
            } else if ("POST".equalsIgnoreCase(method)) {
                Map<String, Object> body = readJsonBody(ex);
                String name = requireBoundedString(getString(body, "name"), "name", MAX_NAME_LENGTH, true);
                Gender gender = Gender.fromCode(getString(body, "gender", "F"));
                int age = getInt(body, "age", -1);
                if (age < 0 || age > MAX_AGE) {
                    throw new ValidationException("Patient age must be between 0 and " + MAX_AGE);
                }
                String contact = requireBoundedString(getString(body, "contactNumber", ""), "contactNumber", 30, false);
                boolean scholarship = getBool(body, "scholarship", false);
                boolean hipertension = getBool(body, "hipertension", false);
                boolean diabetes = getBool(body, "diabetes", false);
                boolean alcoholism = getBool(body, "alcoholism", false);
                int handicap = getInt(body, "handicap", 0);

                Patient saved = patientService.registerPatient(null, name, gender, age, contact,
                        scholarship, hipertension, diabetes, alcoholism, handicap);
                sendJson(ex, 201, Map.of("patient", toPatientMap(saved)));
            } else {
                sendJson(ex, 405, Map.of("error", "Method not allowed"));
            }
        } catch (Exception e) {
            handleApiException(ex, e);
        }
    }

    private void handleDoctors(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        try {
            if ("GET".equalsIgnoreCase(method)) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (Doctor d : doctorService.getAllDoctors()) {
                    list.add(toDoctorMap(d));
                }
                sendJson(ex, 200, Map.of("doctors", list));
            } else if ("POST".equalsIgnoreCase(method)) {
                Map<String, Object> body = readJsonBody(ex);
                String name = requireBoundedString(getString(body, "name"), "name", MAX_NAME_LENGTH, true);
                Gender gender = Gender.fromCode(getString(body, "gender", "F"));
                int age = getInt(body, "age", 40);
                if (age < 18 || age > MAX_AGE) {
                    throw new ValidationException("Doctor age must be between 18 and " + MAX_AGE);
                }
                String specialty = requireBoundedString(getString(body, "specialty", "General Medicine"), "specialty", MAX_TEXT_LENGTH, true);
                String roomNumber = requireBoundedString(getString(body, "roomNumber", "Room 101"), "roomNumber", 40, false);
                String contactNumber = requireBoundedString(getString(body, "contactNumber", ""), "contactNumber", 30, false);
                boolean available = getBool(body, "available", true);

                Doctor saved = doctorService.registerDoctor(null, name, gender, age, specialty, roomNumber, contactNumber, available);
                sendJson(ex, 201, Map.of("doctor", toDoctorMap(saved)));
            } else {
                sendJson(ex, 405, Map.of("error", "Method not allowed"));
            }
        } catch (Exception e) {
            handleApiException(ex, e);
        }
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

    private void handleAppointments(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        try {
            if ("GET".equalsIgnoreCase(method)) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (Appointment a : appointmentService.getAllAppointments()) {
                    list.add(toAppointmentMap(a));
                }
                sendJson(ex, 200, Map.of("appointments", list));
            } else if ("POST".equalsIgnoreCase(method)) {
                Map<String, Object> body = readJsonBody(ex);
                Long patientId = getLong(body, "patientId");
                Long doctorId = getLong(body, "doctorId");
                String dateStr = getString(body, "appointmentDay");
                String timeStr = getString(body, "appointmentTime", "09:30");
                String neighbourhood = requireBoundedString(getString(body, "neighbourhood", "CENTRO"), "neighbourhood", MAX_TEXT_LENGTH, false);
                String notes = requireBoundedString(getString(body, "notes", ""), "notes", MAX_NOTES_LENGTH, false);

                if (patientId == null || doctorId == null || dateStr == null || dateStr.isBlank()) {
                    throw new ValidationException("patientId, doctorId, and appointmentDay (YYYY-MM-DD) are required");
                }
                Doctor doctor = doctorService.getDoctorById(doctorId)
                        .orElseThrow(() -> new EntityNotFoundException("Doctor with ID " + doctorId + " not found"));
                LocalDate apptDate = LocalDate.parse(dateStr.trim());
                LocalTime apptTime = LocalTime.parse(timeStr.trim());

                Appointment booked = appointmentService.bookAppointment(
                        null, patientId, doctor, LocalDateTime.now(), apptDate, apptTime, neighbourhood, notes
                );
                reminderService.syncRemindersWithAppointments();

                List<Map<String, Object>> alternatives = new ArrayList<>();
                if (booked.getPredictionResult() != null && booked.getPredictionResult().getRiskCategory() != RiskCategory.LOW) {
                    for (AppointmentService.LowerRiskSlot slot : appointmentService.findLowerRiskSlots(
                            booked.getPatient(), doctor, LocalDate.now(), 10, neighbourhood)) {
                        if (alternatives.size() >= 3) break;
                        alternatives.add(toSlotMap(slot));
                    }
                }

                int queueLoad = appointmentService.getTodayQueue(LocalDate.now()).size();
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("appointment", toAppointmentMap(booked));
                resp.put("triageGuidance", buildTriageGuidance(booked.getPredictionResult(), booked.getLeadDays(), queueLoad));
                resp.put("recommendedAlternativeSlots", alternatives);
                sendJson(ex, 201, resp);
            } else {
                sendJson(ex, 405, Map.of("error", "Method not allowed"));
            }
        } catch (Exception e) {
            handleApiException(ex, e);
        }
    }

    private void handlePredict(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map<String, Object> body = readJsonBody(ex);
            int age = getInt(body, "age", 35);
            if (age < 0 || age > MAX_AGE) {
                throw new ValidationException("Age must be between 0 and " + MAX_AGE);
            }
            Gender gender = Gender.fromCode(getString(body, "gender", "F"));
            String dateStr = getString(body, "appointmentDay", LocalDate.now().plusDays(7).toString());
            String neighbourhood = requireBoundedString(getString(body, "neighbourhood", "CENTRO"), "neighbourhood", MAX_TEXT_LENGTH, false);
            LocalDate apptDate = LocalDate.parse(dateStr.trim());
            if (apptDate.isBefore(LocalDate.now())) {
                throw new ValidationException("appointmentDay cannot be in the past");
            }

            Patient tempPatient = new Patient(0L, "Simulation", gender, age);
            Doctor tempDoctor = doctorService.getAllDoctors().stream().findFirst()
                    .orElse(new Doctor(201L, "Dr. Beatriz Silva", Gender.F, 42, "Cardiology", "Room 201"));
            Appointment tempAppt = new Appointment(0L, tempPatient, tempDoctor, LocalDateTime.now(), apptDate, neighbourhood);
            PredictionResult pred = predictionService.predict(tempAppt);

            List<Map<String, Object>> alternatives = new ArrayList<>();
            for (AppointmentService.LowerRiskSlot slot : appointmentService.findLowerRiskSlots(
                    tempPatient, tempDoctor, LocalDate.now(), 10, neighbourhood)) {
                if (alternatives.size() >= 3) break;
                alternatives.add(toSlotMap(slot));
            }

            int queueLoad = appointmentService.getTodayQueue(LocalDate.now()).size();
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("leadDays", tempAppt.getLeadDays());
            resp.put("appointmentWeekday", apptDate.getDayOfWeek().name());
            resp.put("todayQueueLoad", queueLoad);
            resp.put("prediction", toPredictionMap(pred));
            resp.put("triageGuidance", buildTriageGuidance(pred, tempAppt.getLeadDays(), queueLoad));
            resp.put("recommendedAlternativeSlots", alternatives);
            sendJson(ex, 200, resp);
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
                sendJson(ex, 200, Map.of("date", LocalDate.now().toString(), "queue", queueList));
                return;
            }
            if (!"POST".equalsIgnoreCase(method)) {
                sendJson(ex, 405, Map.of("error", "Method not allowed"));
                return;
            }
            Map<String, Object> body = readJsonBody(ex);
            Long apptId = getLong(body, "appointmentId");
            if (apptId == null) {
                throw new ValidationException("appointmentId is required");
            }

            Appointment updated;
            switch (path) {
                case "/api/queue/checkin":
                    updated = appointmentService.checkInPatient(apptId);
                    break;
                case "/api/queue/start":
                    updated = appointmentService.startConsultation(apptId);
                    break;
                case "/api/queue/complete":
                    updated = appointmentService.completeAppointment(apptId);
                    break;
                case "/api/queue/noshow":
                    updated = appointmentService.recordNoShow(apptId);
                    break;
                case "/api/queue/cancel":
                    updated = appointmentService.cancelAppointment(apptId);
                    break;
                default:
                    sendJson(ex, 404, Map.of("error", "Unknown queue action"));
                    return;
            }
            sendJson(ex, 200, Map.of("appointment", toAppointmentMap(updated)));
        } catch (Exception e) {
            handleApiException(ex, e);
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
                    list.add(m);
                }
                sendJson(ex, 200, Map.of("reminders", list, "disclaimer", ReminderService.ACADEMIC_DISCLAIMER));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && "/api/reminders/send".equals(path)) {
                Map<String, Object> body = readJsonBody(ex);
                Long reminderId = getLong(body, "reminderId");
                if (reminderId == null) {
                    throw new ValidationException("reminderId is required");
                }
                ReminderRecord sent = reminderService.simulateSendReminder(reminderId);
                sendJson(ex, 200, Map.of("reminderId", sent.getReminderId(), "status", sent.getStatus()));
                return;
            }
            sendJson(ex, 405, Map.of("error", "Method not allowed"));
        } catch (Exception e) {
            handleApiException(ex, e);
        }
    }

    private void handleModel(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        try {
            if ("GET".equalsIgnoreCase(method) && "/api/model/insights".equals(path)) {
                ModelInsightsService.ModelSpec spec = modelInsightsService.getModelSpecification();
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
                sendJson(ex, 200, m);
                return;
            }
            if ("POST".equalsIgnoreCase(method) && "/api/model/retrain".equals(path)) {
                NoShowModelTrainer.TrainingPipelineResult res = modelTrainer.runPipeline();
                predictionService.reloadModel();
                sendJson(ex, 200, Map.of(
                        "status", "RETRAINED",
                        "selectedModel", res.getSelectedResult().getModelName(),
                        "accuracy", res.getSelectedResult().getAccuracy(),
                        "rocArea", res.getSelectedResult().getRocArea()
                ));
                return;
            }
            sendJson(ex, 404, Map.of("error", "Not found"));
        } catch (Exception e) {
            handleApiException(ex, e);
        }
    }

    private void handleAssistantQuery(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map<String, Object> body = readJsonBody(ex);
            String query = requireBoundedString(getString(body, "query"), "query", MAX_NOTES_LENGTH, true);
            Long patientId = getLong(body, "patientId");
            sendJson(ex, 200, assistantService.answerQuery(query, patientId));
        } catch (Exception e) {
            handleApiException(ex, e);
        }
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
                throw new ValidationException("Request body exceeds maximum allowed size (64 KB)");
            }
            String text = new String(raw, StandardCharsets.UTF_8);
            return JsonUtil.parseObject(text);
        }
    }

    private static String requireBoundedString(String val, String fieldName, int maxLength, boolean required) {
        String trimmed = val == null ? "" : val.trim();
        if (required && trimmed.isEmpty()) {
            throw new ValidationException(fieldName + " is required");
        }
        if (trimmed.length() > maxLength) {
            throw new ValidationException(fieldName + " exceeds maximum length of " + maxLength + " characters");
        }
        return trimmed;
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
        if (e instanceof ValidationException || e instanceof IllegalArgumentException || e instanceof java.time.format.DateTimeParseException) {
            sendJson(ex, 400, Map.of("error", e.getMessage()));
        } else if (e instanceof EntityNotFoundException) {
            sendJson(ex, 404, Map.of("error", e.getMessage()));
        } else if (e instanceof DuplicateEntityException) {
            sendJson(ex, 409, Map.of("error", e.getMessage()));
        } else if (e instanceof ClinicFlowException) {
            sendJson(ex, 400, Map.of("error", e.getMessage()));
        } else {
            LOGGER.log(Level.SEVERE, "Unhandled API error", e);
            sendJson(ex, 500, Map.of("error", "Internal server error"));
        }
    }

    private void applySecurityHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("X-Frame-Options", "DENY");
        ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
    }

    private void sendJson(HttpExchange ex, int status, Object payload) throws IOException {
        byte[] bytes = JsonUtil.toJson(payload).getBytes(StandardCharsets.UTF_8);
        applySecurityHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendText(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        applySecurityHeaders(ex);
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
}
