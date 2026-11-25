package com.clinicflow.web;

import com.clinicflow.util.ApplicationConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicFlowWebServerTest {

    @TempDir
    Path tempDir;

    private ClinicFlowWebServer server;
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(tempDir.resolve("data/raw"));
        Files.createDirectories(tempDir.resolve("data/processed"));
        Files.createDirectories(tempDir.resolve("data/app"));
        Files.createDirectories(tempDir.resolve("models"));
        Files.createDirectories(tempDir.resolve("reports"));

        Path sampleSrc = ApplicationConfig.getInstance().getProjectRoot().resolve("data/raw/sample_appointments.csv");
        Files.copy(sampleSrc, tempDir.resolve("data/raw/sample_appointments.csv"));

        ApplicationConfig testConfig = new ApplicationConfig(tempDir, Map.of("CLINICFLOW_HOME", tempDir.toString()));
        server = new ClinicFlowWebServer(testConfig);
        server.start(0);
        baseUrl = "http://127.0.0.1:" + server.getPort();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("GET /health returns UP and modelAvailable=true after startup auto-training")
    void testHealthEndpoint() throws Exception {
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(resp.statusCode()).isEqualTo(200);
        Map<String, Object> json = JsonUtil.parseObject(resp.body());
        assertThat(json.get("status")).isEqualTo("UP");
        assertThat(json.get("modelAvailable")).isEqualTo(true);
    }

    @Test
    @DisplayName("GET / serves accessible HTML UI with semantic landmarks and ARIA attributes")
    void testAccessibleWebUi() throws Exception {
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).contains("<header>", "<nav ", "<main ", "aria-label=", "aria-live=", "aria-pressed=");
        assertThat(resp.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(resp.headers().firstValue("X-Frame-Options")).contains("DENY");
        assertThat(resp.headers().firstValue("Referrer-Policy")).contains("no-referrer");
        assertThat(resp.headers().firstValue("Cross-Origin-Opener-Policy")).contains("same-origin");
        assertThat(resp.headers().firstValue("Cross-Origin-Resource-Policy")).contains("same-origin");
        assertThat(resp.headers().firstValue("Content-Security-Policy"))
                .isPresent()
                .hasValueSatisfying(csp -> assertThat(csp).contains("frame-ancestors 'none'"));
        assertThat(resp.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    @DisplayName("Security & input validation: malformed JSON, invalid age, oversized payload, and CSV formula sanitization")
    void testSecurityAndValidationGuards() throws Exception {
        // 1. Malformed JSON returns 400 without stack trace
        HttpResponse<String> badJson = post("/api/predict", "{invalid-json");
        assertThat(badJson.statusCode()).isEqualTo(400);
        assertThat(badJson.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(badJson.body()).doesNotContain("Exception", "java.lang");

        // 2. Out-of-bounds age returns 400
        HttpResponse<String> badAge = post("/api/predict", "{\"age\":150}");
        assertThat(badAge.statusCode()).isEqualTo(400);

        // 3. Oversized payload (> 64 KB) returns 413 Payload Too Large with unified error envelope
        String huge = "{\"name\":\"" + "A".repeat(70_000) + "\",\"age\":30}";
        HttpResponse<String> hugeResp = post("/api/patients", huge);
        assertThat(hugeResp.statusCode()).isEqualTo(413);
        assertThat(hugeResp.body()).contains("\"code\":413", "\"timestamp\"");

        // 4. Control characters and XSS payloads rejected with 400
        HttpResponse<String> ctrlResp = post("/api/patients", "{\"name\":\"Bad\\u0007Name\",\"age\":30}");
        assertThat(ctrlResp.statusCode()).isEqualTo(400);
        HttpResponse<String> xssResp = post("/api/patients", "{\"name\":\"<script>alert(1)</script>\",\"age\":30}");
        assertThat(xssResp.statusCode()).isEqualTo(400);

        // 4b. Unsupported Content-Type returns 415 Unsupported Media Type
        HttpRequest textReq = HttpRequest.newBuilder(URI.create(baseUrl + "/api/patients"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Alice\",\"age\":30}"))
                .build();
        HttpResponse<String> mediaResp = client.send(textReq, HttpResponse.BodyHandlers.ofString());
        assertThat(mediaResp.statusCode()).isEqualTo(415);

        // 5. Wrong HTTP method returns 405 with Allow header and hardened Cache-Control
        HttpResponse<String> methodResp = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/predict")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(methodResp.statusCode()).isEqualTo(405);
        assertThat(methodResp.headers().firstValue("Allow")).contains("POST");
        assertThat(methodResp.headers().firstValue("Cache-Control")).contains("no-store, no-cache, must-revalidate");
        assertThat(methodResp.headers().firstValue("Pragma")).contains("no-cache");
        assertThat(methodResp.headers().firstValue("Permissions-Policy")).isPresent();
        assertThat(methodResp.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();

        // 6. CSV formula injection sanitizer neutralizes leading triggers while keeping E.164 phone numbers
        assertThat(com.clinicflow.repository.CsvSanitizer.sanitize("=CMD('calc')")).isEqualTo("'=CMD('calc')");
        assertThat(com.clinicflow.repository.CsvSanitizer.sanitize("@SUM(A1:A2)")).isEqualTo("'@SUM(A1:A2)");
        assertThat(com.clinicflow.repository.CsvSanitizer.sanitizeContact("+55-27-99123-4567")).isEqualTo("+55-27-99123-4567");
    }

    @Test
    @DisplayName("POST /api/predict and end-to-end patient, appointment, queue, and validation flow")
    void testSmartAssistantApiFlow() throws Exception {
        // 1. Live prediction simulation
        String futureDate = LocalDate.now().plusDays(10).toString();
        HttpResponse<String> predResp = post("/api/predict",
                "{\"age\":28,\"gender\":\"F\",\"appointmentDay\":\"" + futureDate + "\",\"neighbourhood\":\"CENTRO\"}");
        assertThat(predResp.statusCode()).isEqualTo(200);
        assertThat(predResp.body()).contains("noShowProbability", "riskCategory", "suggestedAction", "recommendedAction", "triageGuidance");

        // 1b. Register doctor
        HttpResponse<String> docResp = post("/api/doctors",
                "{\"name\":\"Dr. Lucas Almeida\",\"specialty\":\"Neurology\",\"roomNumber\":\"Room 402\"}");
        assertThat(docResp.statusCode()).isEqualTo(201);
        assertThat(docResp.body()).contains("Dr. Lucas Almeida");

        // 2. Register patient
        HttpResponse<String> patResp = post("/api/patients",
                "{\"name\":\"Elena Rostova\",\"gender\":\"F\",\"age\":34,\"contactNumber\":\"+55-27-99001-1122\"}");
        assertThat(patResp.statusCode()).isEqualTo(201);
        @SuppressWarnings("unchecked")
        Map<String, Object> patient = (Map<String, Object>) JsonUtil.parseObject(patResp.body()).get("patient");
        long patientId = ((Number) patient.get("id")).longValue();

        // 3. Invalid booking in the past returns 400
        String pastDate = LocalDate.now().minusDays(2).toString();
        HttpResponse<String> badBook = post("/api/appointments",
                "{\"patientId\":" + patientId + ",\"doctorId\":201,\"appointmentDay\":\"" + pastDate + "\"}");
        assertThat(badBook.statusCode()).isEqualTo(400);

        // 4. Valid booking today + check-in + complete
        String today = LocalDate.now().toString();
        HttpResponse<String> bookResp = post("/api/appointments",
                "{\"patientId\":" + patientId + ",\"doctorId\":201,\"appointmentDay\":\"" + today + "\",\"appointmentTime\":\"10:15\"}");
        assertThat(bookResp.statusCode()).isEqualTo(201);
        @SuppressWarnings("unchecked")
        Map<String, Object> appt = (Map<String, Object>) JsonUtil.parseObject(bookResp.body()).get("appointment");
        long apptId = ((Number) appt.get("appointmentId")).longValue();

        HttpResponse<String> checkin = post("/api/queue/checkin", "{\"appointmentId\":" + apptId + "}");
        assertThat(checkin.statusCode()).isEqualTo(200);
        assertThat(checkin.body()).contains("CHECKED_IN");

        HttpResponse<String> complete = post("/api/queue/complete", "{\"appointmentId\":" + apptId + "}");
        assertThat(complete.statusCode()).isEqualTo(200);
        assertThat(complete.body()).contains("ATTENDED");

        // 5. Smart Clinical Assistant context-aware queries across all intents
        HttpResponse<String> briefResp = post("/api/assistant/query", "{\"query\":\"Give me today's clinic briefing\"}");
        assertThat(briefResp.statusCode()).isEqualTo(200);
        assertThat(briefResp.body()).contains("CLINIC_BRIEFING", "reasoningSteps", "clinicContext", "contextSignals", "unremindedHighRiskCount", "busiestDoctor");

        HttpResponse<String> riskResp = post("/api/assistant/query", "{\"query\":\"Who are my highest-risk patients?\"}");
        assertThat(riskResp.statusCode()).isEqualTo(200);
        assertThat(riskResp.body()).contains("HIGH_RISK_TRIAGE");

        HttpResponse<String> remResp = post("/api/assistant/query", "{\"query\":\"Generate today's reminder action plan\"}");
        assertThat(remResp.statusCode()).isEqualTo(200);
        assertThat(remResp.body()).contains("REMINDER_ACTION_PLAN");

        HttpResponse<String> docLoadResp = post("/api/assistant/query", "{\"query\":\"Which doctors are overloaded?\"}");
        assertThat(docLoadResp.statusCode()).isEqualTo(200);
        assertThat(docLoadResp.body()).contains("DOCTOR_LOAD_BALANCING", "Dr. Lucas Almeida");

        HttpResponse<String> docCtxResp = post("/api/assistant/query", "{\"query\":\"Check Dr. Beatriz Silva\"}");
        assertThat(docCtxResp.statusCode()).isEqualTo(200);
        assertThat(docCtxResp.body()).contains("DOCTOR_CONTEXT", "Beatriz Silva");

        HttpResponse<String> patCtxResp = post("/api/assistant/query", "{\"query\":\"Assess patient " + patientId + "\"}");
        assertThat(patCtxResp.statusCode()).isEqualTo(200);
        assertThat(patCtxResp.body()).contains("PATIENT_CONTEXT", "Elena Rostova");

        HttpResponse<String> slotResp = post("/api/assistant/query", "{\"query\":\"Suggest optimal low-risk slots\",\"patientId\":" + patientId + "}");
        assertThat(slotResp.statusCode()).isEqualTo(200);
        assertThat(slotResp.body()).contains("OPTIMAL_SLOT_FINDER", "APPLY_SLOT");

        HttpResponse<String> emptyQuery = post("/api/assistant/query", "{\"query\":\"   \"}");
        assertThat(emptyQuery.statusCode()).isEqualTo(400);

        // 6. Dashboard & model insights
        HttpResponse<String> dash = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/dashboard")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(dash.statusCode()).isEqualTo(200);
        assertThat(dash.body()).contains("\"totalPatients\":1", "\"attendedCount\":1");
    }

    @Test
    @DisplayName("Full queue lifecycle: start consultation, record no-show, and cancel appointment")
    void testQueueLifecycleTransitions() throws Exception {
        HttpResponse<String> patResp = post("/api/patients", "{\"name\":\"Queue Patient\",\"gender\":\"M\",\"age\":45}");
        @SuppressWarnings("unchecked")
        long pid = ((Number) ((Map<String, Object>) JsonUtil.parseObject(patResp.body()).get("patient")).get("id")).longValue();
        String today = LocalDate.now().toString();

        // Book appt 1 -> checkin -> start -> complete
        HttpResponse<String> b1 = post("/api/appointments",
                "{\"patientId\":" + pid + ",\"doctorId\":201,\"appointmentDay\":\"" + today + "\",\"appointmentTime\":\"13:00\"}");
        @SuppressWarnings("unchecked")
        long a1 = ((Number) ((Map<String, Object>) JsonUtil.parseObject(b1.body()).get("appointment")).get("appointmentId")).longValue();
        assertThat(post("/api/queue/checkin", "{\"appointmentId\":" + a1 + "}").statusCode()).isEqualTo(200);
        assertThat(post("/api/queue/start", "{\"appointmentId\":" + a1 + "}").body()).contains("IN_CONSULTATION");
        assertThat(post("/api/queue/complete", "{\"appointmentId\":" + a1 + "}").body()).contains("ATTENDED");

        // Book appt 2 -> noshow
        HttpResponse<String> b2 = post("/api/appointments",
                "{\"patientId\":" + pid + ",\"doctorId\":201,\"appointmentDay\":\"" + today + "\",\"appointmentTime\":\"14:00\"}");
        @SuppressWarnings("unchecked")
        long a2 = ((Number) ((Map<String, Object>) JsonUtil.parseObject(b2.body()).get("appointment")).get("appointmentId")).longValue();
        assertThat(post("/api/queue/noshow", "{\"appointmentId\":" + a2 + "}").body()).contains("NO_SHOW");

        // Book appt 3 -> cancel
        HttpResponse<String> b3 = post("/api/appointments",
                "{\"patientId\":" + pid + ",\"doctorId\":201,\"appointmentDay\":\"" + today + "\",\"appointmentTime\":\"15:00\"}");
        @SuppressWarnings("unchecked")
        long a3 = ((Number) ((Map<String, Object>) JsonUtil.parseObject(b3.body()).get("appointment")).get("appointmentId")).longValue();
        assertThat(post("/api/queue/cancel", "{\"appointmentId\":" + a3 + "}").body()).contains("CANCELLED");
    }

    @Test
    @DisplayName("Per-IP rate limiter returns 429 Too Many Requests when threshold is exceeded")
    void testRateLimiting() throws Exception {
        int lastStatus = 200;
        for (int i = 0; i < 125; i++) {
            HttpResponse<String> r = client.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/api/doctors")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            lastStatus = r.statusCode();
            if (lastStatus == 429) {
                break;
            }
        }
        assertThat(lastStatus).isEqualTo(429);
    }

    @Test
    @DisplayName("Negative API paths: 404 unknown route, non-existent IDs, double-booking conflict, and model retrain")
    void testNegativeRoutesAndModelEndpoints() throws Exception {
        // 1. Unknown API route returns 404
        HttpResponse<String> notFound = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/nonexistent")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(notFound.statusCode()).isEqualTo(404);

        // 2. Non-existent appointment/reminder ID returns 404 JSON error
        assertThat(post("/api/queue/checkin", "{\"appointmentId\":999999}").statusCode()).isEqualTo(404);
        assertThat(post("/api/reminders/send", "{\"reminderId\":999999}").statusCode()).isEqualTo(404);

        // 3. Double-booking same doctor/date/time returns 400/409 JSON error
        HttpResponse<String> pat = post("/api/patients", "{\"name\":\"Double Book Test\",\"gender\":\"F\",\"age\":29}");
        @SuppressWarnings("unchecked")
        long pid = ((Number) ((Map<String, Object>) JsonUtil.parseObject(pat.body()).get("patient")).get("id")).longValue();
        String day = LocalDate.now().plusDays(2).toString();
        assertThat(post("/api/appointments",
                "{\"patientId\":" + pid + ",\"doctorId\":201,\"appointmentDay\":\"" + day + "\",\"appointmentTime\":\"11:00\"}").statusCode())
                .isEqualTo(201);
        HttpResponse<String> dup = post("/api/appointments",
                "{\"patientId\":" + pid + ",\"doctorId\":201,\"appointmentDay\":\"" + day + "\",\"appointmentTime\":\"11:00\"}");
        assertThat(dup.statusCode()).isIn(400, 409);

        // 4. Model insights and retrain endpoints succeed
        HttpResponse<String> insights = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/model/insights")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(insights.statusCode()).isEqualTo(200);
        assertThat(insights.body()).contains("RandomForest", "features", "accuracy");

        HttpResponse<String> retrain = post("/api/model/retrain", "{}");
        assertThat(retrain.statusCode()).isEqualTo(200);
        assertThat(retrain.body()).contains("RETRAINED");
    }

    @Test
    @DisplayName("WebSecuritySupport bounds tracked IP buckets at MAX_TRACKED_IPS (2048)")
    void testRateLimiterMemoryBound() {
        WebSecuritySupport sec = new WebSecuritySupport();
        for (int i = 0; i < 2500; i++) {
            assertThat(sec.allowIp("10.0." + (i / 255) + "." + (i % 255))).isTrue();
        }
        assertThat(sec.getTrackedIpCount()).isLessThanOrEqualTo(2048);
    }

    @Test
    @DisplayName("Concurrent throughput: 25 parallel /api/predict and /api/assistant/query calls succeed cleanly")
    void testConcurrentPredictionAndAssistantThroughput() throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            java.util.List<java.util.concurrent.CompletableFuture<Integer>> futures = new java.util.ArrayList<>();
            String futureDate = LocalDate.now().plusDays(5).toString();
            for (int i = 0; i < 25; i++) {
                final int idx = i;
                futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        HttpResponse<String> r = (idx % 2 == 0)
                                ? post("/api/predict", "{\"age\":35,\"gender\":\"F\",\"appointmentDay\":\"" + futureDate + "\"}")
                                : post("/api/assistant/query", "{\"query\":\"Give me today's clinic briefing\"}");
                        return r.statusCode();
                    } catch (Exception e) {
                        return 500;
                    }
                }, pool));
            }
            for (java.util.concurrent.CompletableFuture<Integer> f : futures) {
                assertThat(f.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
