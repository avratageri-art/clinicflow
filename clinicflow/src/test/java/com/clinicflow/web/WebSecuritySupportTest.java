package com.clinicflow.web;

import com.clinicflow.util.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebSecuritySupportTest {

    @Test
    @DisplayName("requireBoundedString accepts valid text and rejects null, blank, oversized, control chars, and XSS")
    void testRequireBoundedString() {
        assertThat(WebSecuritySupport.requireBoundedString("  Maria Santos ", "name", 50, true))
                .isEqualTo("Maria Santos");
        assertThat(WebSecuritySupport.requireBoundedString(null, "notes", 50, false)).isEmpty();

        assertThatThrownBy(() -> WebSecuritySupport.requireBoundedString("  ", "name", 50, true))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> WebSecuritySupport.requireBoundedString("abcdef", "name", 5, true))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> WebSecuritySupport.requireBoundedString("bad\u0007text", "name", 50, true))
                .isInstanceOf(ValidationException.class);

        for (String payload : new String[]{"<script>alert(1)</script>", "</script>", "javascript:alert(1)",
                "img onerror=alert(1)", "body onload=x()", "<iframe src=x>"}) {
            assertThatThrownBy(() -> WebSecuritySupport.requireBoundedString(payload, "name", 100, true))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Test
    @DisplayName("requirePositiveId rejects null, zero, and negative IDs")
    void testRequirePositiveId() {
        assertThat(WebSecuritySupport.requirePositiveId(1001L, "patientId")).isEqualTo(1001L);
        assertThatThrownBy(() -> WebSecuritySupport.requirePositiveId(null, "patientId"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> WebSecuritySupport.requirePositiveId(0L, "patientId"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> WebSecuritySupport.requirePositiveId(-5L, "patientId"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Per-IP rate limiter enforces 120 req/min and caps tracked IPs at 2048")
    void testRateLimiterWindowAndMemoryCap() {
        WebSecuritySupport sec = new WebSecuritySupport();
        for (int i = 0; i < 120; i++) {
            assertThat(sec.allowIp("192.168.1.10")).isTrue();
        }
        assertThat(sec.allowIp("192.168.1.10")).isFalse();

        for (int i = 0; i < 2500; i++) {
            sec.allowIp("10.1." + (i / 255) + "." + (i % 255));
        }
        assertThat(sec.getTrackedIpCount()).isLessThanOrEqualTo(2048);
    }
}
