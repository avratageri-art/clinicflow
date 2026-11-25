package com.clinicflow.ml;

import com.clinicflow.util.DatasetLoadingException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Loads CSV datasets safely using Apache Commons CSV parser.
 * Handles UTF-8 encoding, header mapping, and strict file verification.
 */
public class CsvDatasetLoader {

    public static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(false)
            .setTrim(true)
            .build();

    /**
     * Loads all records from the target CSV file.
     *
     * @param csvFile file pointer
     * @return list of parsed CSV records
     * @throws DatasetLoadingException if file is missing, empty, or unreadable
     */
    public List<CSVRecord> loadRecords(File csvFile) {
        if (csvFile == null || !csvFile.exists()) {
            throw new DatasetLoadingException("CSV dataset file does not exist: " +
                    (csvFile != null ? csvFile.getAbsolutePath() : "null"));
        }
        if (csvFile.length() == 0) {
            throw new DatasetLoadingException("CSV dataset file is empty (0 bytes): " + csvFile.getAbsolutePath());
        }

        List<CSVRecord> records = new ArrayList<>(115000);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8));
             CSVParser parser = new CSVParser(reader, CSV_FORMAT)) {

            for (CSVRecord record : parser) {
                records.add(record);
            }
            return records;
        } catch (IOException e) {
            throw new DatasetLoadingException("Failed to read dataset file: " + csvFile.getAbsolutePath(), e);
        }
    }

    /**
     * Retrieves header names from the CSV file.
     */
    public List<String> getHeaders(File csvFile) {
        if (csvFile == null || !csvFile.exists()) {
            throw new DatasetLoadingException("CSV dataset file does not exist: " +
                    (csvFile != null ? csvFile.getAbsolutePath() : "null"));
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8));
             CSVParser parser = new CSVParser(reader, CSV_FORMAT)) {

            Map<String, Integer> headerMap = parser.getHeaderMap();
            if (headerMap == null || headerMap.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> headers = new ArrayList<>(headerMap.keySet());
            return headers;
        } catch (IOException e) {
            throw new DatasetLoadingException("Failed to extract headers from: " + csvFile.getAbsolutePath(), e);
        }
    }
}
