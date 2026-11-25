package com.clinicflow.repository;

import com.clinicflow.model.ReminderAction;
import com.clinicflow.model.ReminderRecord;
import com.clinicflow.model.RiskCategory;
import com.clinicflow.util.ApplicationConfig;
import com.clinicflow.util.ClinicFlowException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * CSV-backed persistent repository for {@link ReminderRecord} entries.
 * Stores reminder logs and simulation history in data/app/reminders.csv.
 */
public class CsvReminderRepository implements ReminderRepository {

    private static final String[] HEADERS = {
            "ReminderId", "AppointmentId", "PatientName", "PatientContact",
            "AppointmentDate", "AppointmentTime", "DoctorName", "RiskCategory",
            "RecommendedAction", "Status", "SentTimestamp", "Notes"
    };

    private final File csvFile;
    private final Map<Long, ReminderRecord> cache = new ConcurrentHashMap<>();

    public CsvReminderRepository() {
        this(ApplicationConfig.getInstance().getAppDataPath().resolve("reminders.csv").toFile());
    }

    public CsvReminderRepository(File csvFile) {
        this.csvFile = csvFile;
        initFileAndLoad();
    }

    private synchronized void initFileAndLoad() {
        try {
            if (!csvFile.exists()) {
                if (csvFile.getParentFile() != null) {
                    csvFile.getParentFile().mkdirs();
                }
                saveAllToCsv(Collections.emptyList());
            } else {
                loadFromCsv();
            }
        } catch (IOException e) {
            throw new ClinicFlowException("Failed to initialize reminder CSV storage: " + csvFile.getAbsolutePath(), e);
        }
    }

    private void loadFromCsv() throws IOException {
        cache.clear();
        if (!csvFile.exists() || csvFile.length() == 0) return;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8));
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {

            for (CSVRecord rec : parser) {
                try {
                    Long remId = Long.parseLong(rec.get("ReminderId"));
                    Long apptId = Long.parseLong(rec.get("AppointmentId"));
                    String patientName = rec.get("PatientName");
                    String contact = rec.isMapped("PatientContact") ? rec.get("PatientContact") : "N/A";
                    LocalDate apptDate = LocalDate.parse(rec.get("AppointmentDate"));
                    LocalTime apptTime = rec.isMapped("AppointmentTime") && !rec.get("AppointmentTime").isEmpty() ?
                            LocalTime.parse(rec.get("AppointmentTime")) : LocalTime.of(9, 0);
                    String doctorName = rec.get("DoctorName");
                    RiskCategory risk = RiskCategory.valueOf(rec.get("RiskCategory").toUpperCase());
                    ReminderAction action = ReminderAction.valueOf(rec.get("RecommendedAction").toUpperCase());
                    String status = rec.get("Status");

                    LocalDateTime sentTime = null;
                    if (rec.isMapped("SentTimestamp") && !rec.get("SentTimestamp").isEmpty() && !"null".equalsIgnoreCase(rec.get("SentTimestamp"))) {
                        sentTime = LocalDateTime.parse(rec.get("SentTimestamp"), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    }
                    String notes = rec.isMapped("Notes") ? rec.get("Notes") : "";

                    ReminderRecord r = new ReminderRecord(
                            remId, apptId, patientName, contact, apptDate, apptTime,
                            doctorName, risk, action, status, sentTime, notes
                    );
                    cache.put(remId, r);
                } catch (Exception ex) {
                    System.err.println("Skipping malformed reminder record in CSV: " + ex.getMessage());
                }
            }
        }
    }

    private synchronized void flushToDisk() {
        try {
            saveAllToCsv(new ArrayList<>(cache.values()));
        } catch (IOException e) {
            throw new ClinicFlowException("Failed to persist reminders to CSV: " + csvFile.getAbsolutePath(), e);
        }
    }

    private void saveAllToCsv(List<ReminderRecord> reminders) throws IOException {
        File tempFile = new File(csvFile.getAbsolutePath() + ".tmp");
        try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(osw, CSVFormat.DEFAULT.builder().setHeader(HEADERS).build())) {

            for (ReminderRecord r : reminders) {
                String sentStr = r.getSentTimestamp() != null ? r.getSentTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "";
                printer.printRecord(
                        r.getReminderId(),
                        r.getAppointmentId(),
                        CsvSanitizer.sanitize(r.getPatientName()),
                        CsvSanitizer.sanitizeContact(r.getPatientContact()),
                        r.getAppointmentDate().toString(),
                        r.getAppointmentTime() != null ? r.getAppointmentTime().toString() : "09:00:00",
                        CsvSanitizer.sanitize(r.getDoctorName()),
                        r.getRiskCategory().name(),
                        r.getRecommendedAction().name(),
                        r.getStatus(),
                        sentStr,
                        CsvSanitizer.sanitize(r.getNotes())
                );
            }
            printer.flush();
        }
        Files.move(tempFile.toPath(), csvFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public synchronized long generateNextId() {
        return cache.keySet().stream().mapToLong(v -> v).max().orElse(100L) + 1;
    }

    @Override
    public synchronized ReminderRecord save(ReminderRecord entity) {
        if (entity == null) {
            throw new IllegalArgumentException("ReminderRecord cannot be null");
        }
        cache.put(entity.getId(), entity);
        flushToDisk();
        return entity;
    }

    @Override
    public Optional<ReminderRecord> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(cache.get(id));
    }

    @Override
    public List<ReminderRecord> findAll() {
        return new ArrayList<>(cache.values());
    }

    @Override
    public boolean existsById(Long id) {
        if (id == null) return false;
        return cache.containsKey(id);
    }

    @Override
    public synchronized boolean deleteById(Long id) {
        if (id == null) return false;
        ReminderRecord removed = cache.remove(id);
        if (removed != null) {
            flushToDisk();
            return true;
        }
        return false;
    }

    @Override
    public long count() {
        return cache.size();
    }

    @Override
    public synchronized void clear() {
        cache.clear();
        flushToDisk();
    }

    @Override
    public List<ReminderRecord> findByAppointmentId(Long appointmentId) {
        if (appointmentId == null) return List.of();
        return cache.values().stream()
                .filter(r -> r.getAppointmentId().equals(appointmentId))
                .collect(Collectors.toList());
    }

    @Override
    public List<ReminderRecord> findPendingReminders() {
        return cache.values().stream()
                .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ReminderRecord> findSentReminders() {
        return cache.values().stream()
                .filter(r -> "SENT".equalsIgnoreCase(r.getStatus()))
                .collect(Collectors.toList());
    }
}
