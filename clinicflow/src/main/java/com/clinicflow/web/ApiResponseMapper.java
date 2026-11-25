package com.clinicflow.web;

import com.clinicflow.model.*;
import com.clinicflow.service.AppointmentService;
import com.clinicflow.service.ModelInsightsService;

import java.util.*;

/**
 * Pure serialization and request-extraction mapper for {@link ClinicFlowWebServer}.
 * Converts domain entities, ML predictions, and model metadata into JSON-ready maps
 * without coupling HTTP transport logic to domain DTO formatting.
 */
final class ApiResponseMapper {

    private static final String DEFAULT_TIME = "09:00";
    private static final String UNKNOWN = "Unknown";
    private static final int HIGH_QUEUE_LOAD_THRESHOLD = 10;

    private ApiResponseMapper() {
    }

    static Map<String, Object> toPatientMap(Patient p) {
        Objects.requireNonNull(p, "patient cannot be null");
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

    static Map<String, Object> toDoctorMap(Doctor d) {
        Objects.requireNonNull(d, "doctor cannot be null");
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

    static Map<String, Object> toAppointmentMap(Appointment a) {
        Objects.requireNonNull(a, "appointment cannot be null");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("appointmentId", a.getAppointmentId());
        m.put("queueToken", a.getQueueToken());
        m.put("patientId", a.getPatient() != null ? a.getPatient().getId() : null);
        m.put("patientName", a.getPatient() != null ? a.getPatient().getName() : UNKNOWN);
        m.put("doctorId", a.getDoctor() != null ? a.getDoctor().getId() : null);
        m.put("doctorName", a.getDoctor() != null ? a.getDoctor().getName() : UNKNOWN);
        m.put("specialty", a.getDoctor() != null ? a.getDoctor().getSpecialty() : "");
        m.put("appointmentDay", a.getAppointmentDay().toString());
        m.put("appointmentTime", a.getAppointmentTime() != null ? a.getAppointmentTime().toString() : DEFAULT_TIME);
        m.put("leadDays", a.getLeadDays());
        m.put("neighbourhood", a.getNeighbourhood());
        m.put("attendanceStatus", a.getAttendanceStatus().name());
        m.put("reminderStatus", a.getReminderStatus());
        m.put("prediction", toPredictionMap(a.getPredictionResult()));
        return m;
    }

    static Map<String, Object> toPredictionMap(PredictionResult pred) {
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
        String actionName = pred.getSuggestedAction() != null ? pred.getSuggestedAction().name() : null;
        m.put("suggestedAction", actionName);
        m.put("recommendedAction", actionName);
        m.put("actionDescription", pred.getSuggestedAction() != null
                ? pred.getSuggestedAction().getActionDescription()
                : pred.getStatusMessage());
        return m;
    }

    static Map<String, Object> toReminderMap(ReminderRecord r) {
        Objects.requireNonNull(r, "reminder cannot be null");
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
        return m;
    }

    static Map<String, Object> toModelInsightsMap(ModelInsightsService.ModelSpec spec, boolean modelAvailable) {
        Objects.requireNonNull(spec, "model specification cannot be null");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("modelAvailable", modelAvailable);
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
        return m;
    }

    static Map<String, Object> toSlotMap(AppointmentService.LowerRiskSlot slot) {
        Objects.requireNonNull(slot, "slot cannot be null");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", slot.getDate().toString());
        m.put("time", slot.getTime().toString());
        m.put("leadDays", slot.getLeadDays());
        m.put("noShowProbability", slot.getNoShowProbability());
        m.put("riskCategory", slot.getRiskCategory().name());
        m.put("suggestedAction", slot.getSuggestedAction().name());
        return m;
    }

    static String buildTriageGuidance(PredictionResult pred, long leadDays, int todayQueueLoad) {
        if (pred == null || !pred.isModelAvailable()) {
            return "Advisory only: ML model unavailable; apply standard clinic confirmation.";
        }
        RiskCategory risk = pred.getRiskCategory();
        String leadCtx = leadDays == 0 ? "Same-day booking" : leadDays + "-day lead time";
        String queueCtx = todayQueueLoad >= HIGH_QUEUE_LOAD_THRESHOLD
                ? "High today queue load (" + todayQueueLoad + " active)"
                : "Normal today queue load (" + todayQueueLoad + " active)";
        if (risk == RiskCategory.HIGH) {
            return leadCtx + " | " + queueCtx + " — Prioritize coordinator phone call and prepare optional backfill slot (advisory only; queue order remains strict FIFO).";
        }
        if (risk == RiskCategory.MEDIUM) {
            return leadCtx + " | " + queueCtx + " — Send two-way SMS confirmation link and offer lower-risk alternative dates if helpful (advisory only).";
        }
        return leadCtx + " | " + queueCtx + " — Standard automated 24h SMS reminder is sufficient (advisory only).";
    }

    static String getString(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : defaultVal;
    }

    static int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val != null) {
            try { return Integer.parseInt(val.toString().trim()); } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    static Long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).longValue();
        if (val != null && !val.toString().isBlank()) {
            try { return Long.parseLong(val.toString().trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    static boolean getBool(Map<String, Object> map, String key, boolean defaultVal) {
        Object val = map.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        return val != null ? Boolean.parseBoolean(val.toString()) : defaultVal;
    }
}
