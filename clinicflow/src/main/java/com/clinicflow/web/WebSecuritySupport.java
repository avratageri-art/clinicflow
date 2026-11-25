package com.clinicflow.web;

import com.clinicflow.util.ValidationException;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared HTTP security, rate-limiting, input validation, and JSON error-envelope helpers
 * for {@link ClinicFlowWebServer}.
 */
final class WebSecuritySupport {

    private static final int MAX_REQUESTS_PER_MINUTE = 120;
    private static final long WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<String, WindowCounter> ipCounters = new ConcurrentHashMap<>();

    /**
     * Enforces a lightweight per-IP sliding-window rate limit for API endpoints.
     */
    boolean allowRequest(HttpExchange ex) {
        String ip = ex.getRemoteAddress() != null && ex.getRemoteAddress().getAddress() != null
                ? ex.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
        long now = System.currentTimeMillis();
        WindowCounter counter = ipCounters.compute(ip, (k, existing) -> {
            if (existing == null || now - existing.windowStartMs >= WINDOW_MS) {
                return new WindowCounter(now);
            }
            return existing;
        });
        return counter.count.incrementAndGet() <= MAX_REQUESTS_PER_MINUTE;
    }

    void applySecurityHeaders(HttpExchange ex, boolean isApi) {
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("X-Frame-Options", "DENY");
        ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        ex.getResponseHeaders().set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if (isApi) {
            ex.getResponseHeaders().set("Cache-Control", "no-store");
        }
    }

    void sendJson(HttpExchange ex, int status, Object payload, boolean isApi) throws IOException {
        byte[] bytes = JsonUtil.toJson(payload).getBytes(StandardCharsets.UTF_8);
        applySecurityHeaders(ex, isApi);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    void sendError(HttpExchange ex, int status, String message, String allowHeader) throws IOException {
        if (allowHeader != null && !allowHeader.isBlank()) {
            ex.getResponseHeaders().set("Allow", allowHeader);
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("error", message != null ? message : "Request failed");
        envelope.put("code", status);
        envelope.put("timestamp", LocalDateTime.now().toString());
        sendJson(ex, status, envelope, true);
    }

    static String requireBoundedString(String val, String fieldName, int maxLength, boolean required) {
        String trimmed = val == null ? "" : val.trim();
        if (required && trimmed.isEmpty()) {
            throw new ValidationException(fieldName + " is required");
        }
        if (trimmed.length() > maxLength) {
            throw new ValidationException(fieldName + " exceeds maximum length of " + maxLength + " characters");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < 0x20 && c != '\n' && c != '\t') {
                throw new ValidationException(fieldName + " contains invalid control characters");
            }
        }
        return trimmed;
    }

    private static final class WindowCounter {
        final long windowStartMs;
        final AtomicInteger count = new AtomicInteger(0);

        WindowCounter(long windowStartMs) {
            this.windowStartMs = windowStartMs;
        }
    }
}
