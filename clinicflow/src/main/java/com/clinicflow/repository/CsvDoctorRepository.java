package com.clinicflow.repository;

import com.clinicflow.model.Doctor;
import com.clinicflow.model.Gender;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * CSV-backed persistent repository for {@link Doctor} records.
 * Retains records in data/app/doctors.csv across application restarts.
 * Thread-safe with atomic file updates.
 */
public class CsvDoctorRepository implements DoctorRepository {

    private static final String[] HEADERS = {
            "DoctorId", "Name", "Gender", "Age", "Specialty", "RoomNumber", "ContactNumber", "Available"
    };

    private final File csvFile;
    private final Map<Long, Doctor> cache = new ConcurrentHashMap<>();

    public CsvDoctorRepository() {
        this(ApplicationConfig.getInstance().getAppDataPath().resolve("doctors.csv").toFile());
    }

    public CsvDoctorRepository(File csvFile) {
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
            throw new ClinicFlowException("Failed to initialize doctor CSV storage: " + csvFile.getAbsolutePath(), e);
        }
    }

    private void loadFromCsv() throws IOException {
        cache.clear();
        if (!csvFile.exists() || csvFile.length() == 0) return;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8));
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {

            for (CSVRecord rec : parser) {
                try {
                    Long id = Long.parseLong(rec.get("DoctorId"));
                    String name = rec.get("Name");
                    Gender gender = Gender.valueOf(rec.get("Gender").toUpperCase());
                    int age = Integer.parseInt(rec.get("Age"));
                    String specialty = rec.get("Specialty");
                    String room = rec.get("RoomNumber");
                    String contact = rec.isMapped("ContactNumber") ? rec.get("ContactNumber") : "";
                    boolean available = rec.isMapped("Available") ? Boolean.parseBoolean(rec.get("Available")) : true;

                    Doctor doc = new Doctor(id, name, gender, age, specialty, room, contact, available);
                    cache.put(id, doc);
                } catch (Exception ex) {
                    System.err.println("Skipping malformed doctor record in CSV: " + ex.getMessage());
                }
            }
        }
    }

    private synchronized void flushToDisk() {
        try {
            saveAllToCsv(new ArrayList<>(cache.values()));
        } catch (IOException e) {
            throw new ClinicFlowException("Failed to persist doctors to CSV: " + csvFile.getAbsolutePath(), e);
        }
    }

    private void saveAllToCsv(List<Doctor> doctors) throws IOException {
        File tempFile = new File(csvFile.getAbsolutePath() + ".tmp");
        try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(osw, CSVFormat.DEFAULT.builder().setHeader(HEADERS).build())) {

            for (Doctor d : doctors) {
                printer.printRecord(
                        d.getId(),
                        CsvSanitizer.sanitize(d.getName()),
                        d.getGender().name(),
                        d.getAge(),
                        CsvSanitizer.sanitize(d.getSpecialty()),
                        CsvSanitizer.sanitize(d.getRoomNumber()),
                        CsvSanitizer.sanitizeContact(d.getContactNumber()),
                        d.isAvailable()
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
    public synchronized Doctor save(Doctor entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Doctor cannot be null");
        }
        cache.put(entity.getId(), entity);
        flushToDisk();
        return entity;
    }

    @Override
    public Optional<Doctor> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(cache.get(id));
    }

    @Override
    public List<Doctor> findAll() {
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
        Doctor removed = cache.remove(id);
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
    public List<Doctor> findAvailableDoctors() {
        return cache.values().stream()
                .filter(Doctor::isAvailable)
                .collect(Collectors.toList());
    }

    @Override
    public List<Doctor> findBySpecialty(String specialty) {
        if (specialty == null || specialty.trim().isEmpty()) {
            return findAll();
        }
        return cache.values().stream()
                .filter(d -> d.getSpecialty().equalsIgnoreCase(specialty.trim()))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Doctor> findByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Optional.empty();
        }
        return cache.values().stream()
                .filter(d -> d.getName().equalsIgnoreCase(name.trim()))
                .findFirst();
    }
}
