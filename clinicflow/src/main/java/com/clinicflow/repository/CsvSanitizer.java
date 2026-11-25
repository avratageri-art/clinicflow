package com.clinicflow.repository;

/**
 * Prevents CSV formula/spreadsheet injection by neutralizing leading formula trigger characters.
 */
public final class CsvSanitizer {

    private CsvSanitizer() {
    }

    /**
     * Neutralizes leading '=', '+', '-', '@', tab, or carriage-return characters in free-text cells.
     */
    public static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        char first = trimmed.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + trimmed;
        }
        return trimmed;
    }

    /**
     * Neutralizes formula injection in phone/contact fields while preserving valid E.164 '+' prefixes.
     */
    public static String sanitizeContact(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        char first = trimmed.charAt(0);
        if (first == '+' && trimmed.length() > 1 && Character.isDigit(trimmed.charAt(1))) {
            return trimmed;
        }
        return sanitize(trimmed);
    }
}
