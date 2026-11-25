package com.clinicflow.model;

/**
 * Attendance status for appointments.
 */
public enum AttendanceStatus {
    SCHEDULED("Scheduled, outcome pending"),
    CHECKED_IN("Patient checked in, waiting in queue"),
    IN_CONSULTATION("Consultation in progress with doctor"),
    ATTENDED("Patient attended and completed consultation"),
    NO_SHOW("Patient did not attend appointment"),
    CANCELLED("Appointment cancelled");

    private final String description;

    AttendanceStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCompleted() {
        return this == ATTENDED;
    }

    public boolean isFinished() {
        return this == ATTENDED || this == NO_SHOW || this == CANCELLED;
    }

    public boolean isInQueue() {
        return this == CHECKED_IN || this == IN_CONSULTATION;
    }

    public static AttendanceStatus fromDatasetNoShow(String noShow) {
        if (noShow == null) {
            return SCHEDULED;
        }
        String trimmed = noShow.trim();
        if ("Yes".equalsIgnoreCase(trimmed) || "1".equals(trimmed) || "true".equalsIgnoreCase(trimmed)) {
            return NO_SHOW;
        } else if ("No".equalsIgnoreCase(trimmed) || "0".equals(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return ATTENDED;
        }
        return SCHEDULED;
    }
}
