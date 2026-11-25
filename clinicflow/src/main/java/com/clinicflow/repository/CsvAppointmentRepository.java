package com.clinicflow.repository;

import com.clinicflow.model.*;
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
 * CSV-backed persistent repository for {@link Appointment} records.
 * Retains appointments, ML predictions, queue tokens, and statuses in data/app/appointments.csv.
 * Thread-safe with atomic file updates.
 */
public class CsvAppointmentRepository implements AppointmentRepository {

    private static final String[] HEADERS = {
            "AppointmentId", "PatientId", "DoctorId", "ScheduledDay", "AppointmentDay",
            "AppointmentTime", "QueueToken", "Neighbourhood", "SmsReceived", "AttendanceStatus",
            "PredictedClass", "NoShowProb", "AttendanceProb", "RiskCategory", "SuggestedAction",
            "Notes", "CheckInTime", "ReminderStatus"
    };

    private final File csvFile;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final Map<Long, Appointment> cache = new ConcurrentHashMap<>();

    public CsvAppointmentRepository(PatientRepository patientRepository, DoctorRepository doctorRepository) {
        this(ApplicationConfig.getInstance().getAppDataPath().resolve("appointments.csv").toFile(),
                patientRepository, doctorRepository);
    }

    public CsvAppointmentRepository(File csvFile, PatientRepository patientRepository, DoctorRepository doctorRepository) {
        this.csvFile = csvFile;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
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
            throw new ClinicFlowException("Failed to initialize appointment CSV storage: " + csvFile.getAbsolutePath(), e);
        }
    }

    private void loadFromCsv() throws IOException {
        cache.clear();
        if (!csvFile.exists() || csvFile.length() == 0) return;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8));
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {

            for (CSVRecord rec : parser) {
                try {
                    Long apptId = Long.parseLong(rec.get("AppointmentId"));
                    Long patientId = Long.parseLong(rec.get("PatientId"));
                    Long doctorId = Long.parseLong(rec.get("DoctorId"));
                    LocalDateTime sched = LocalDateTime.parse(rec.get("ScheduledDay"), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    LocalDate apptDay = LocalDate.parse(rec.get("AppointmentDay"));
                    LocalTime apptTime = rec.isMapped("AppointmentTime") && !rec.get("AppointmentTime").isEmpty() ?
                            LocalTime.parse(rec.get("AppointmentTime")) : LocalTime.of(9, 0);
                    String token = rec.isMapped("QueueToken") ? rec.get("QueueToken") : "Q-" + (apptId % 1000);
                    String neighbourhood = rec.get("Neighbourhood");
                    boolean sms = Boolean.parseBoolean(rec.get("SmsReceived"));
                    AttendanceStatus status = AttendanceStatus.valueOf(rec.get("AttendanceStatus").toUpperCase());

                    String notes = rec.isMapped("Notes") ? rec.get("Notes") : "";
                    LocalDateTime checkInTime = null;
                    if (rec.isMapped("CheckInTime") && !rec.get("CheckInTime").isEmpty() && !"null".equalsIgnoreCase(rec.get("CheckInTime"))) {
                        checkInTime = LocalDateTime.parse(rec.get("CheckInTime"), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    }
                    String reminderStatus = rec.isMapped("ReminderStatus") ? rec.get("ReminderStatus") : "PENDING";

                    // Resolve Patient & Doctor
                    Patient patient = patientRepository.findById(patientId).orElseGet(() ->
                            new Patient(patientId, "Patient #" + patientId, Gender.F, 30, "N/A"));
                    Doctor doctor = doctorRepository.findById(doctorId).orElseGet(() ->
                            new Doctor(doctorId, "Dr. Assigned (ID " + doctorId + ")", Gender.M, 45, "General Medicine", "Room 101"));

                    Appointment appt = new Appointment(
                            apptId, patient, doctor, sched, apptDay, apptTime,
                            token, neighbourhood, sms, status, notes, checkInTime, reminderStatus
                    );

                    // Reconstruct PredictionResult
                    if (rec.isMapped("NoShowProb") && !rec.get("NoShowProb").isEmpty()) {
                        String probStr = rec.get("NoShowProb");
                        if (!"NaN".equalsIgnoreCase(probStr)) {
                            double noShowProb = Double.parseDouble(probStr);
                            double attProb = rec.isMapped("AttendanceProb") && !rec.get("AttendanceProb").isEmpty() ?
                                    Double.parseDouble(rec.get("AttendanceProb")) : 1.0 - noShowProb;
                            PredictionResult pr = PredictionResult.fromProbabilities(attProb, noShowProb);
                            appt.setPredictionResult(pr);
                        } else {
                            appt.setPredictionResult(PredictionResult.unavailable());
                        }
                    }

                    cache.put(apptId, appt);
                } catch (Exception ex) {
                    System.err.println("Skipping malformed appointment record in CSV: " + ex.getMessage());
                }
            }
        }
    }

    private synchronized void flushToDisk() {
        try {
            saveAllToCsv(new ArrayList<>(cache.values()));
        } catch (IOException e) {
            throw new ClinicFlowException("Failed to persist appointments to CSV: " + csvFile.getAbsolutePath(), e);
        }
    }

    private void saveAllToCsv(List<Appointment> appointments) throws IOException {
        File tempFile = new File(csvFile.getAbsolutePath() + ".tmp");
        try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(osw, CSVFormat.DEFAULT.builder().setHeader(HEADERS).build())) {

            for (Appointment a : appointments) {
                Long patientId = a.getPatient() != null ? a.getPatient().getId() : 0L;
                Long doctorId = a.getDoctor() != null ? a.getDoctor().getId() : 0L;
                PredictionResult pr = a.getPredictionResult();

                String predClass = pr != null ? pr.getPredictedClass() : "UNAVAILABLE";
                String noShowProb = pr != null && pr.isModelAvailable() ? String.format(Locale.US, "%.4f", pr.getNoShowProbability()) : "NaN";
                String attProb = pr != null && pr.isModelAvailable() ? String.format(Locale.US, "%.4f", pr.getAttendanceProbability()) : "NaN";
                String risk = pr != null ? pr.getRiskCategory().name() : RiskCategory.LOW.name();
                String action = pr != null ? pr.getSuggestedAction().name() : ReminderAction.STANDARD.name();
                String checkInStr = a.getCheckInTime() != null ? a.getCheckInTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "";

                printer.printRecord(
                        a.getAppointmentId(),
                        patientId,
                        doctorId,
                        a.getScheduledDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        a.getAppointmentDay().toString(),
                        a.getAppointmentTime() != null ? a.getAppointmentTime().toString() : "09:00:00",
                        a.getQueueToken(),
                        a.getNeighbourhood(),
                        a.isSmsReceived(),
                        a.getAttendanceStatus().name(),
                        predClass,
                        noShowProb,
                        attProb,
                        risk,
                        action,
                        a.getNotes() != null ? a.getNotes() : "",
                        checkInStr,
                        a.getReminderStatus()
                );
            }
            printer.flush();
        }
        Files.move(tempFile.toPath(), csvFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public synchronized long generateNextId() {
        return cache.keySet().stream().mapToLong(v -> v).max().orElse(5000L) + 1;
    }

    @Override
    public synchronized Appointment save(Appointment entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Appointment cannot be null");
        }
        cache.put(entity.getId(), entity);
        flushToDisk();
        return entity;
    }

    @Override
    public Optional<Appointment> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(cache.get(id));
    }

    @Override
    public List<Appointment> findAll() {
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
        Appointment removed = cache.remove(id);
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
    public List<Appointment> findByPatientId(Long patientId) {
        if (patientId == null) return List.of();
        return cache.values().stream()
                .filter(a -> a.getPatient() != null && a.getPatient().getId().equals(patientId))
                .collect(Collectors.toList());
    }

    @Override
    public List<Appointment> findByAppointmentDay(LocalDate date) {
        if (date == null) return List.of();
        return cache.values().stream()
                .filter(a -> a.getAppointmentDay().equals(date))
                .collect(Collectors.toList());
    }

    @Override
    public List<Appointment> findByStatus(AttendanceStatus status) {
        if (status == null) return List.of();
        return cache.values().stream()
                .filter(a -> a.getAttendanceStatus() == status)
                .collect(Collectors.toList());
    }

    public List<Appointment> findByDoctorIdAndDay(Long doctorId, LocalDate date) {
        if (doctorId == null || date == null) return List.of();
        return cache.values().stream()
                .filter(a -> a.getDoctor() != null && a.getDoctor().getId().equals(doctorId) && a.getAppointmentDay().equals(date))
                .collect(Collectors.toList());
    }

    public Optional<Appointment> findByDoctorAndDateTime(Long doctorId, LocalDate date, LocalTime time) {
        if (doctorId == null || date == null || time == null) return Optional.empty();
        return cache.values().stream()
                .filter(a -> a.getDoctor() != null && a.getDoctor().getId().equals(doctorId)
                        && a.getAppointmentDay().equals(date)
                        && a.getAppointmentTime() != null
                        && a.getAppointmentTime().getHour() == time.getHour()
                        && a.getAppointmentTime().getMinute() == time.getMinute()
                        && a.getAttendanceStatus() != AttendanceStatus.CANCELLED)
                .findFirst();
    }

    public List<Appointment> findTodayQueue(LocalDate date) {
        if (date == null) return List.of();
        return cache.values().stream()
                .filter(a -> a.getAppointmentDay().equals(date) && a.getAttendanceStatus().isInQueue())
                .sorted(Comparator.comparing(Appointment::getCheckInTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Appointment::getAppointmentTime))
                .collect(Collectors.toList());
    }
}
