package com.clinicflow.web;

import com.clinicflow.ml.*;
import com.clinicflow.model.Gender;
import com.clinicflow.repository.*;
import com.clinicflow.service.*;
import com.clinicflow.util.ApplicationConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight, Cloud Run / Render-ready HTTP web server for ClinicFlow.
 * Focuses strictly on HTTP server lifecycle, route dispatch, and transport security.
 */
public class ClinicFlowWebServer {

    private static final Logger LOGGER = Logger.getLogger(ClinicFlowWebServer.class.getName());
    private static final String BIND_HOST = "0.0.0.0";
    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_PORT = 65535;
    private static final int EXECUTOR_THREADS = 8;
    private static final int HTTP_OK = 200;
    private static final int HTTP_NO_CONTENT = 204;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_METHOD_NOT_ALLOWED = 405;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_SERVER_ERROR = 500;

    private final DoctorService doctorService;
    private final ReminderService reminderService;
    private final WekaNoShowPredictionService predictionService;
    private final NoShowModelTrainer modelTrainer;
    private final WebSecuritySupport security = new WebSecuritySupport();
    private final ClinicApiHandlers api;

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
        PatientService patientService = new PatientService(patientRepo, apptRepo);
        this.doctorService = new DoctorService(doctorRepo, apptRepo);
        AppointmentService appointmentService = new AppointmentService(patientRepo, apptRepo, doctorRepo, new DataValidator(), predictionService);
        this.reminderService = new ReminderService(reminderRepo, apptRepo);
        ModelInsightsService insightsService = new ModelInsightsService(config);
        ClinicalAssistantService assistantService = new ClinicalAssistantService(patientService, doctorService, appointmentService, reminderService, predictionService);
        this.modelTrainer = new NoShowModelTrainer(new DataPreprocessor(), new ModelEvaluator(), new ModelRepository(), config);
        this.api = new ClinicApiHandlers(patientService, doctorService, appointmentService, reminderService,
                insightsService, assistantService, predictionService, modelTrainer, security);
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
        server.createContext("/api/dashboard", wrapApi(api::handleDashboard));
        server.createContext("/api/patients", wrapApi(api::handlePatients));
        server.createContext("/api/doctors", wrapApi(api::handleDoctors));
        server.createContext("/api/appointments", wrapApi(api::handleAppointments));
        server.createContext("/api/predict", wrapApi(api::handlePredict));
        server.createContext("/api/queue", wrapApi(api::handleQueue));
        server.createContext("/api/reminders", wrapApi(api::handleReminders));
        server.createContext("/api/model", wrapApi(api::handleModel));
        server.createContext("/api/assistant/query", wrapApi(api::handleAssistantQuery));
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
                ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                ex.sendResponseHeaders(HTTP_NO_CONTENT, -1);
                ex.close();
                return;
            }
            if (!security.allowRequest(ex)) {
                security.sendError(ex, HTTP_TOO_MANY_REQUESTS, "Rate limit exceeded (max 120 requests/minute)", null);
                return;
            }
            delegate.handle(ex);
        };
    }

    private void handleRoot(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            security.sendError(ex, HTTP_METHOD_NOT_ALLOWED, "Method not allowed", "GET");
            return;
        }
        String path = ex.getRequestURI().getPath();
        if (!"/".equals(path) && !"/index.html".equals(path)) {
            security.sendError(ex, HTTP_NOT_FOUND, "Not found", null);
            return;
        }
        try (InputStream is = getClass().getResourceAsStream("/web/index.html")) {
            if (is == null) {
                security.sendError(ex, HTTP_SERVER_ERROR, "Web UI resource missing", null);
                return;
            }
            byte[] html = is.readAllBytes();
            security.applySecurityHeaders(ex, false);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'");
            ex.sendResponseHeaders(HTTP_OK, html.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(html);
            }
        }
    }

    private void handleHealth(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            security.sendError(ex, HTTP_METHOD_NOT_ALLOWED, "Method not allowed", "GET");
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "UP");
        payload.put("service", "ClinicFlow");
        payload.put("modelAvailable", predictionService.isModelAvailable());
        payload.put("timestamp", LocalDateTime.now().toString());
        security.sendJson(ex, HTTP_OK, payload, true);
    }
}
