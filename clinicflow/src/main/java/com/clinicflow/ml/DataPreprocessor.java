package com.clinicflow.ml;

import com.clinicflow.util.DatasetLoadingException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import weka.core.Instances;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Data preprocessing engine.
 * 1. Safely parses raw appointment records.
 * 2. Investigates and filters verifiable entry anomalies (Age = -1, negative lead days).
 * 3. Writes cleaned data to data/processed/clean_appointments.csv and produces audit report.
 * 4. Performs a chronological 80:20 train/test split.
 * 5. Generates Weka-compatible training and testing Instances.
 */
public class DataPreprocessor {

    private final CsvDatasetLoader loader;
    private final FeatureExtractor featureExtractor;

    public DataPreprocessor() {
        this.loader = new CsvDatasetLoader();
        this.featureExtractor = new FeatureExtractor();
    }

    public DataPreprocessor(FeatureExtractor featureExtractor) {
        this.loader = new CsvDatasetLoader();
        this.featureExtractor = featureExtractor;
    }

    /**
     * Executes the full data cleaning workflow.
     *
     * @param rawCsvFile  source raw CSV file (data/raw/appointments.csv)
     * @param cleanCsvFile destination cleaned CSV (data/processed/clean_appointments.csv)
     * @param reportFile   destination cleaning audit report (reports/data-cleaning-report.md)
     * @return cleaning summary containing counts and itemized audit records
     */
    public CleaningSummary cleanDataset(File rawCsvFile, File cleanCsvFile, File reportFile) {
        if (!rawCsvFile.exists()) {
            throw new DatasetLoadingException("Raw CSV file not found: " + rawCsvFile.getAbsolutePath());
        }

        List<CSVRecord> rawRecords = loader.loadRecords(rawCsvFile);
        List<String> headers = loader.getHeaders(rawCsvFile);

        List<CSVRecord> validRecords = new ArrayList<>(rawRecords.size());
        List<CleaningRecord> excludedRecords = new ArrayList<>();

        int attendedCount = 0;
        int noShowCount = 0;

        for (CSVRecord record : rawRecords) {
            long apptId = Long.parseLong(record.get("AppointmentID"));
            long patientId = (long) Double.parseDouble(record.get("PatientId"));
            int age = Integer.parseInt(record.get("Age"));
            String schedStr = record.get("ScheduledDay");
            String apptStr = record.get("AppointmentDay");
            String rawNoShow = record.get("No-show").trim();

            LocalDateTime sched = LocalDateTime.parse(schedStr, DateTimeFormatter.ISO_DATE_TIME);
            LocalDate appt = LocalDate.parse(apptStr.substring(0, 10));
            long leadDays = ChronoUnit.DAYS.between(sched.toLocalDate(), appt);

            // Anomaly Check 1: Negative Age
            if (age < 0) {
                excludedRecords.add(new CleaningRecord(
                        apptId, patientId,
                        "Negative patient age (" + age + ") is biologically impossible (data entry error)",
                        schedStr, apptStr, age, leadDays, rawNoShow
                ));
                continue;
            }

            // Anomaly Check 2: Negative Lead Days (Appointment before booking)
            if (leadDays < 0) {
                excludedRecords.add(new CleaningRecord(
                        apptId, patientId,
                        "Appointment date precedes scheduling date (leadDays=" + leadDays + ", scheduled " +
                                sched.toLocalDate() + " but appointment " + appt + ")",
                        schedStr, apptStr, age, leadDays, rawNoShow
                ));
                continue;
            }

            // Valid record
            validRecords.add(record);
            if ("Yes".equalsIgnoreCase(rawNoShow)) {
                noShowCount++;
            } else {
                attendedCount++;
            }
        }

        // Write cleaned CSV
        cleanCsvFile.getParentFile().mkdirs();
        try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(cleanCsvFile), StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(osw, CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build())) {

            for (CSVRecord rec : validRecords) {
                printer.printRecord(rec);
            }
            printer.flush();
        } catch (IOException e) {
            throw new DatasetLoadingException("Failed to write clean CSV: " + cleanCsvFile.getAbsolutePath(), e);
        }

        // Create summary
        CleaningSummary summary = new CleaningSummary(
                rawRecords.size(),
                validRecords.size(),
                excludedRecords,
                attendedCount,
                noShowCount,
                featureExtractor.getFeatureNames()
        );

        // Save report
        try {
            summary.saveToFile(reportFile);
        } catch (IOException e) {
            throw new DatasetLoadingException("Failed to write cleaning report: " + reportFile.getAbsolutePath(), e);
        }

        return summary;
    }

    /**
     * Splits cleaned records chronologically into training (80%) and testing (20%) partitions.
     * Sorts strictly by ScheduledDay to prevent future leakage into past records.
     *
     * @param cleanCsvFile source cleaned CSV file
     * @return preprocessed split result containing train and test Weka Instances
     */
    public PreprocessedData splitChronologically(File cleanCsvFile, double trainRatio) {
        if (!cleanCsvFile.exists()) {
            throw new DatasetLoadingException("Cleaned CSV file not found: " + cleanCsvFile.getAbsolutePath());
        }

        List<CSVRecord> records = loader.loadRecords(cleanCsvFile);

        // Sort chronologically by ScheduledDay timestamp
        records.sort(Comparator.comparing(r -> r.get("ScheduledDay")));

        int total = records.size();
        int splitIndex = (int) Math.round(total * trainRatio);

        List<CSVRecord> trainRecords = records.subList(0, splitIndex);
        List<CSVRecord> testRecords = records.subList(splitIndex, total);

        Instances trainInstances = featureExtractor.getDatasetHeader();
        for (CSVRecord r : trainRecords) {
            trainInstances.add(featureExtractor.extractInstance(r));
        }

        Instances testInstances = featureExtractor.getDatasetHeader();
        for (CSVRecord r : testRecords) {
            testInstances.add(featureExtractor.extractInstance(r));
        }

        return new PreprocessedData(trainRecords, testRecords, trainInstances, testInstances);
    }

    /**
     * Container holding the chronological train/test records and Weka Instances.
     */
    public static class PreprocessedData {
        private final List<CSVRecord> trainRecords;
        private final List<CSVRecord> testRecords;
        private final Instances trainInstances;
        private final Instances testInstances;

        public PreprocessedData(List<CSVRecord> trainRecords, List<CSVRecord> testRecords,
                                Instances trainInstances, Instances testInstances) {
            this.trainRecords = trainRecords;
            this.testRecords = testRecords;
            this.trainInstances = trainInstances;
            this.testInstances = testInstances;
        }

        public List<CSVRecord> getTrainRecords() { return trainRecords; }
        public List<CSVRecord> getTestRecords() { return testRecords; }
        public Instances getTrainInstances() { return trainInstances; }
        public Instances getTestInstances() { return testInstances; }
        public int getTrainCount() { return trainRecords.size(); }
        public int getTestCount() { return testRecords.size(); }
    }
}
