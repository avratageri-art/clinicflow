package com.clinicflow.ml;

import com.clinicflow.util.DatasetLoadingException;
import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Executes deep statistical and data quality inspection on the raw appointments dataset.
 * Produces an exact {@link DatasetProfile} with computed metrics.
 */
public class DatasetInspector {

    private final CsvDatasetLoader loader;

    public DatasetInspector() {
        this.loader = new CsvDatasetLoader();
    }

    public DatasetInspector(CsvDatasetLoader loader) {
        this.loader = loader;
    }

    /**
     * Inspects the given dataset file and calculates all required profile statistics.
     *
     * @param csvFile dataset file pointer
     * @return populated {@link DatasetProfile}
     */
    public DatasetProfile inspect(File csvFile) {
        if (csvFile == null || !csvFile.exists()) {
            throw new DatasetLoadingException("Target CSV file does not exist: " +
                    (csvFile != null ? csvFile.getAbsolutePath() : "null"));
        }

        DatasetProfile profile = new DatasetProfile();
        profile.setDatasetFilePath(csvFile.getAbsolutePath());
        profile.setFileSizeBytes(csvFile.length());

        List<String> headers = loader.getHeaders(csvFile);
        profile.setColumnNames(headers);
        profile.setTotalColumns(headers.size());

        List<CSVRecord> records = loader.loadRecords(csvFile);
        profile.setTotalRecords(records.size());

        // Initialize missing value counters
        Map<String, Integer> missingCounts = new LinkedHashMap<>();
        for (String h : headers) {
            missingCounts.put(h, 0);
        }

        Set<String> uniqueApptIds = new HashSet<>(records.size());
        Set<String> uniquePatientIds = new HashSet<>(records.size());
        int duplicateAppts = 0;

        int minAge = Integer.MAX_VALUE;
        int maxAge = Integer.MIN_VALUE;
        long sumAge = 0;
        int negativeAgeCount = 0;
        int centenarianCount = 0;

        int femaleCount = 0;
        int maleCount = 0;

        LocalDateTime earliestScheduled = null;
        LocalDateTime latestScheduled = null;
        LocalDate earliestAppt = null;
        LocalDate latestAppt = null;

        long minLead = Long.MAX_VALUE;
        long maxLead = Long.MIN_VALUE;
        long sumLead = 0;
        int sameDayCount = 0;
        int negativeLeadCount = 0;

        int scholarshipCount = 0;
        int hipertensionCount = 0;
        int diabetesCount = 0;
        int alcoholismCount = 0;
        Map<Integer, Integer> handicapMap = new HashMap<>();
        int smsReceivedCount = 0;

        int showedUpCount = 0;
        int noShowCount = 0;

        Map<String, Integer> neighbourhoodCounts = new HashMap<>();

        for (CSVRecord rec : records) {
            // Check missing values
            for (String h : headers) {
                String val = rec.get(h);
                if (val == null || val.trim().isEmpty()) {
                    missingCounts.put(h, missingCounts.get(h) + 1);
                }
            }

            // ID tracking
            String apptId = rec.get("AppointmentID");
            if (!uniqueApptIds.add(apptId)) {
                duplicateAppts++;
            }
            String patientId = rec.get("PatientId");
            uniquePatientIds.add(patientId);

            // Age
            int age = Integer.parseInt(rec.get("Age"));
            if (age < minAge) minAge = age;
            if (age > maxAge) maxAge = age;
            sumAge += age;
            if (age < 0) negativeAgeCount++;
            if (age > 100) centenarianCount++;

            // Gender
            String gender = rec.get("Gender").trim().toUpperCase();
            if ("F".equals(gender)) femaleCount++;
            else if ("M".equals(gender)) maleCount++;

            // Dates & Waiting days
            LocalDateTime sched = LocalDateTime.parse(rec.get("ScheduledDay"), DateTimeFormatter.ISO_DATE_TIME);
            LocalDate appt = LocalDate.parse(rec.get("AppointmentDay").substring(0, 10));

            if (earliestScheduled == null || sched.isBefore(earliestScheduled)) earliestScheduled = sched;
            if (latestScheduled == null || sched.isAfter(latestScheduled)) latestScheduled = sched;

            if (earliestAppt == null || appt.isBefore(earliestAppt)) earliestAppt = appt;
            if (latestAppt == null || appt.isAfter(latestAppt)) latestAppt = appt;

            long lead = ChronoUnit.DAYS.between(sched.toLocalDate(), appt);
            if (lead < minLead) minLead = lead;
            if (lead > maxLead) maxLead = lead;
            sumLead += lead;
            if (lead == 0) sameDayCount++;
            if (lead < 0) negativeLeadCount++;

            // Clinical flags
            if ("1".equals(rec.get("Scholarship"))) scholarshipCount++;
            if ("1".equals(rec.get("Hipertension"))) hipertensionCount++;
            if ("1".equals(rec.get("Diabetes"))) diabetesCount++;
            if ("1".equals(rec.get("Alcoholism"))) alcoholismCount++;

            int handcap = Integer.parseInt(rec.get("Handcap"));
            handicapMap.put(handcap, handicapMap.getOrDefault(handcap, 0) + 1);

            if ("1".equals(rec.get("SMS_received"))) smsReceivedCount++;

            // Target variable
            String noShow = rec.get("No-show").trim();
            if ("Yes".equalsIgnoreCase(noShow)) {
                noShowCount++;
            } else {
                showedUpCount++;
            }

            // Neighbourhood
            String neigh = rec.get("Neighbourhood").trim();
            neighbourhoodCounts.put(neigh, neighbourhoodCounts.getOrDefault(neigh, 0) + 1);
        }

        profile.setMissingValuesPerColumn(missingCounts);
        profile.setDuplicateAppointmentIds(duplicateAppts);
        profile.setUniquePatientsCount(uniquePatientIds.size());

        profile.setMinAge(minAge);
        profile.setMaxAge(maxAge);
        profile.setMeanAge(records.isEmpty() ? 0 : (double) sumAge / records.size());
        profile.setNegativeAgeCount(negativeAgeCount);
        profile.setCentenarianCount(centenarianCount);

        profile.setFemaleCount(femaleCount);
        profile.setMaleCount(maleCount);

        profile.setEarliestScheduledDay(earliestScheduled != null ? earliestScheduled.toString() : "N/A");
        profile.setLatestScheduledDay(latestScheduled != null ? latestScheduled.toString() : "N/A");
        profile.setEarliestAppointmentDay(earliestAppt != null ? earliestAppt.toString() : "N/A");
        profile.setLatestAppointmentDay(latestAppt != null ? latestAppt.toString() : "N/A");

        profile.setMinLeadDays(minLead);
        profile.setMaxLeadDays(maxLead);
        profile.setMeanLeadDays(records.isEmpty() ? 0 : (double) sumLead / records.size());
        profile.setSameDayAppointmentsCount(sameDayCount);
        profile.setNegativeLeadDaysCount(negativeLeadCount);

        profile.setScholarshipCount(scholarshipCount);
        profile.setHipertensionCount(hipertensionCount);
        profile.setDiabetesCount(diabetesCount);
        profile.setAlcoholismCount(alcoholismCount);
        profile.setHandicapDistribution(handicapMap);
        profile.setSmsReceivedCount(smsReceivedCount);

        profile.setShowedUpCount(showedUpCount);
        profile.setNoShowCount(noShowCount);

        // Sort top 10 neighbourhoods
        Map<String, Integer> top10Neighbourhoods = neighbourhoodCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
        profile.setTopNeighbourhoods(top10Neighbourhoods);

        return profile;
    }
}
