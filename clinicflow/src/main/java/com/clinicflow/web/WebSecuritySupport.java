package com.clinicflow.web;

import com.clinicflow.util.ValidationException;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private static final List<String> DANGEROUS_HTML_PATTERNS = List.of(
            "<script", "</script", "javascript:", "onerror=", "onload=", "<iframe"
    );

    private final ConcurrentHashMap<String, WindowCounter> ipCounters = new ConcurrentHashMap<>();

    /**
     * Enforces a lightweight per-IP sliding-window rate limit for API endpoints.
     *
     * @param ex HTTP exchange
     * @return {@code true} if within limit, {@code false} if rate-limited
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

    /**
     * Validates that {@code POST} requests to JSON APIs declare {@code application/json}.
     *
     * @param ex HTTP exchange
     * @return {@code true} if valid or non-POST, {@code false} if unsupported media type
     */
    boolean hasValidJsonContentType(HttpExchange ex) {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            return true;
        }
        String ct = ex.getRequestHeaders().getFirst("Content-Type");
        if (ct == null) {
            return false;
        }
        return ct.toLowerCase(Locale.ROOT).trim().startsWith("application/json");
    }

    void applySecurityHeaders(HttpExchange ex, boolean isApi) {
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("X-Frame-Options", "DENY");
        ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        ex.getResponseHeaders().set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        ex.getResponseHeaders().set("Cross-Origin-Opener-Policy", "same-origin");
        ex.getResponseHeaders().set("Cross-Origin-Resource-Policy", "same-origin");
        if (isApi) {
            ex.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
            ex.getResponseHeaders().set("Pragma", "no-cache");
        }
    }

    void sendJson(HttpExchange ex, int status, Object payload, boolean isApi) throws IOException {
        byte[] bytes = JsonUtil.toJson(payload).getBytes(StandardCharsets.UTF_8);
        applySecurityHeaders(ex, isApi);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
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
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String pattern : DANGEROUS_HTML_PATTERNS) {
            if (lower.contains(pattern)) {
                throw new ValidationException(fieldName + " contains disallowed script or HTML content");
            }
        }
        return trimmed;
    }

    static long requirePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new ValidationException(fieldName + " must be a positive integer");
        }
        return id;
    }

    private static final class WindowCounter {
        final long windowStartMs;
        final AtomicInteger count = new AtomicInteger(0);

        WindowCounter(long windowStartMs) {
            this.windowStartMs = windowStartMs;
        }
    }
}
