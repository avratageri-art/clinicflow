package com.clinicflow.repository;

import com.clinicflow.model.Gender;
import com.clinicflow.model.Patient;
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
 * CSV-backed persistent repository for {@link Patient} records.
 * Retains records in data/app/patients.csv across application restarts.
 * Thread-safe with atomic file updates.
 */
public class CsvPatientRepository implements PatientRepository {

    private static final String[] HEADERS = {
            "PatientId", "Name", "Gender", "Age", "ContactNumber",
            "Scholarship", "Hipertension", "Diabetes", "Alcoholism", "Handicap"
    };

    private final File csvFile;
    private final Map<Long, Patient> cache = new ConcurrentHashMap<>();

    public CsvPatientRepository() {
        this(ApplicationConfig.getInstance().getAppDataPath().resolve("patients.csv").toFile());
    }

    public CsvPatientRepository(File csvFile) {
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
            throw new ClinicFlowException("Failed to initialize patient CSV storage: " + csvFile.getAbsolutePath(), e);
        }
    }

    private void loadFromCsv() throws IOException {
        cache.clear();
        if (!csvFile.exists() || csvFile.length() == 0) return;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8));
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {

            for (CSVRecord rec : parser) {
                try {
                    Long id = Long.parseLong(rec.get("PatientId"));
                    String name = rec.get("Name");
                    Gender gender = Gender.valueOf(rec.get("Gender").toUpperCase());
                    int age = Integer.parseInt(rec.get("Age"));
                    String contact = rec.isMapped("ContactNumber") ? rec.get("ContactNumber") : "";
                    boolean scholarship = Boolean.parseBoolean(rec.get("Scholarship"));
                    boolean hipertension = Boolean.parseBoolean(rec.get("Hipertension"));
                    boolean diabetes = Boolean.parseBoolean(rec.get("Diabetes"));
                    boolean alcoholism = Boolean.parseBoolean(rec.get("Alcoholism"));
                    int handicap = Integer.parseInt(rec.get("Handicap"));

                    Patient p = new Patient(id, name, gender, age, scholarship, hipertension, diabetes, alcoholism, handicap, contact);
                    cache.put(id, p);
                } catch (Exception ex) {
                    System.err.println("Skipping malformed patient record in CSV: " + ex.getMessage());
                }
            }
        }
    }

    private synchronized void flushToDisk() {
        try {
            saveAllToCsv(new ArrayList<>(cache.values()));
        } catch (IOException e) {
            throw new ClinicFlowException("Failed to persist patients to CSV: " + csvFile.getAbsolutePath(), e);
        }
    }

    private void saveAllToCsv(List<Patient> patients) throws IOException {
        File tempFile = new File(csvFile.getAbsolutePath() + ".tmp");
        try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(osw, CSVFormat.DEFAULT.builder().setHeader(HEADERS).build())) {

            for (Patient p : patients) {
                printer.printRecord(
                        p.getId(),
                        CsvSanitizer.sanitize(p.getName()),
                        p.getGender().name(),
                        p.getAge(),
                        CsvSanitizer.sanitizeContact(p.getContactNumber()),
                        p.isScholarship(),
                        p.isHipertension(),
                        p.isDiabetes(),
                        p.isAlcoholism(),
                        p.getHandicap()
                );
            }
            printer.flush();
        }
        Files.move(tempFile.toPath(), csvFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public synchronized long generateNextId() {
        return cache.keySet().stream().mapToLong(v -> v).max().orElse(1000L) + 1;
    }

    @Override
    public synchronized Patient save(Patient entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Patient cannot be null");
        }
        cache.put(entity.getId(), entity);
        flushToDisk();
        return entity;
    }

    @Override
    public Optional<Patient> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(cache.get(id));
    }

    @Override
    public List<Patient> findAll() {
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
        Patient removed = cache.remove(id);
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
    public List<Patient> findByNameContaining(String namePart) {
        if (namePart == null || namePart.trim().isEmpty()) {
            return findAll();
        }
        String lower = namePart.trim().toLowerCase();
        return cache.values().stream()
                .filter(p -> p.getName().toLowerCase().contains(lower))
                .collect(Collectors.toList());
    }

    @Override
    public List<Patient> findByHasChronicConditions() {
        return cache.values().stream()
                .filter(Patient::hasChronicConditions)
                .collect(Collectors.toList());
    }
}
