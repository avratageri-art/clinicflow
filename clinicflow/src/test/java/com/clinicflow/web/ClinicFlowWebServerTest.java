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
        assertThat(resp.headers().firstValue("Content-Security-Policy")).isPresent();
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

        // 3. Oversized payload (> 64 KB) returns 400
        String huge = "{\"name\":\"" + "A".repeat(70_000) + "\",\"age\":30}";
        HttpResponse<String> hugeResp = post("/api/patients", huge);
        assertThat(hugeResp.statusCode()).isEqualTo(400);

        // 4. CSV formula injection sanitizer neutralizes leading triggers while keeping E.164 phone numbers
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

        // 5. Dashboard & model insights
        HttpResponse<String> dash = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/dashboard")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(dash.statusCode()).isEqualTo(200);
        assertThat(dash.body()).contains("\"totalPatients\":1", "\"attendedCount\":1");
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
