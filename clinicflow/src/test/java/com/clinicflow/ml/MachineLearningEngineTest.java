package com.clinicflow.ml;

import com.clinicflow.model.Appointment;
import com.clinicflow.model.Doctor;
import com.clinicflow.model.Gender;
import com.clinicflow.model.Patient;
import com.clinicflow.model.PredictionResult;
import com.clinicflow.model.ReminderAction;
import com.clinicflow.model.RiskCategory;
import com.clinicflow.service.NoShowPredictionService;
import com.clinicflow.service.WekaNoShowPredictionService;
import com.clinicflow.util.ApplicationConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import weka.classifiers.rules.ZeroR;
import weka.classifiers.trees.RandomForest;
import weka.core.Instance;
import weka.core.Instances;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MachineLearningEngineTest {

    private static ApplicationConfig config;
    private static File sampleCsvFile;

    @BeforeAll
    static void init() {
        config = ApplicationConfig.getInstance();
        sampleCsvFile = config.getProjectRoot()
                .resolve("data").resolve("raw").resolve("sample_appointments.csv").toFile();
        assertThat(sampleCsvFile).exists().isFile();
    }

    @Test
    @DisplayName("FeatureExtractor generates exact schema with zero identifiers or target leakage")
    void testFeatureSchemaIntegrity() {
        FeatureExtractor extractor = new FeatureExtractor();
        Instances header = extractor.getDatasetHeader();

        // 6 predictive features + 1 target class = 7 attributes
        assertThat(header.numAttributes()).isEqualTo(7);
        assertThat(header.classIndex()).isEqualTo(6);

        List<String> featureNames = extractor.getFeatureNames();
        assertThat(featureNames).containsExactly(
                "leadTimeDays",
                "appointmentWeekday",
                "bookingWeekday",
                "bookingHour",
                "sameDayBooking",
                "age"
        );

        // Strict Leakage Audits
        assertThat(featureNames).doesNotContain("PatientId");
        assertThat(featureNames).doesNotContain("AppointmentID");
        assertThat(featureNames).doesNotContain("SMS_received");
        assertThat(featureNames).doesNotContain("No-show");
        assertThat(featureNames).doesNotContain("noShow");

        assertThat(extractor.getTargetAttributeName()).isEqualTo("noShow");
        assertThat(extractor.getClassValues()).containsExactly("NO", "YES");
    }

    @Test
    @DisplayName("FeatureExtractor extracts unlabelled instance for live appointment with missing class")
    void testLiveAppointmentFeatureExtraction() {
        FeatureExtractor extractor = new FeatureExtractor();
        Patient patient = new Patient(101L, "Test Patient", Gender.F, 42);
        Doctor doctor = new Doctor(1L, "Dr. Test", Gender.M, 45, "General", "101");

        LocalDateTime sched = LocalDateTime.of(2026, 6, 1, 14, 30);
        LocalDate appt = LocalDate.of(2026, 6, 8); // 7 days later
        Appointment appointment = new Appointment(501L, patient, doctor, sched, appt, "CENTRO");

        Instance instance = extractor.extractInstance(appointment);

        assertThat(instance.value(0)).isEqualTo(7.0);                // leadTimeDays
        assertThat(instance.stringValue(1)).isEqualTo("MONDAY");     // apptWeekday
        assertThat(instance.stringValue(2)).isEqualTo("MONDAY");     // schedWeekday
        assertThat(instance.value(3)).isEqualTo(14.0);               // bookingHour
        assertThat(instance.stringValue(4)).isEqualTo("NO");         // sameDayBooking
        assertThat(instance.value(5)).isEqualTo(42.0);               // age
        assertThat(instance.isMissing(6)).isTrue();                  // class is missing ('?')
    }

    @Test
    @DisplayName("DataPreprocessor cleans raw CSV and isolates exact synthetic anomaly records")
    void testDataCleaningAnomalies(@TempDir Path tempDir) throws IOException {
        File fixtureCsv = tempDir.resolve("raw-anomaly-fixture.csv").toFile();
        StringBuilder csv = new StringBuilder();
        csv.append("PatientId,AppointmentID,Gender,ScheduledDay,AppointmentDay,Age,Neighbourhood,Scholarship,Hipertension,Diabetes,Alcoholism,Handcap,SMS_received,No-show\n");
        // 20 valid records
        for (int i = 1; i <= 20; i++) {
            csv.append(String.format("100%d,200%d,F,2026-05-01T09:00:00Z,2026-05-05T00:00:00Z,%d,CENTRO,0,0,0,0,0,0,No\n", i, i, 20 + i));
        }
        // 1 negative age anomaly
        csv.append("9001,9901,F,2026-05-01T09:00:00Z,2026-05-05T00:00:00Z,-1,CENTRO,0,0,0,0,0,0,No\n");
        // 2 negative lead-day anomalies (ScheduledDay after AppointmentDay)
        csv.append("9002,9902,M,2026-05-10T09:00:00Z,2026-05-05T00:00:00Z,30,CENTRO,0,0,0,0,0,0,No\n");
        csv.append("9003,9903,F,2026-05-12T10:00:00Z,2026-05-05T00:00:00Z,40,CENTRO,0,0,0,0,0,0,Yes\n");
        Files.writeString(fixtureCsv.toPath(), csv.toString(), StandardCharsets.UTF_8);

        DataPreprocessor preprocessor = new DataPreprocessor();
        File cleanCsv = tempDir.resolve("clean.csv").toFile();
        File reportMd = tempDir.resolve("cleaning-report.md").toFile();

        CleaningSummary summary = preprocessor.cleanDataset(fixtureCsv, cleanCsv, reportMd);

        assertThat(summary.getOriginalRecordCount()).isEqualTo(23);
        assertThat(summary.getExcludedRecordCount()).isEqualTo(3);
        assertThat(summary.getValidRecordCount()).isEqualTo(20);

        assertThat(cleanCsv).exists();
        assertThat(reportMd).exists();

        List<CleaningRecord> excluded = summary.getExcludedRecords();
        assertThat(excluded).hasSize(3);

        long ageAnomalyCount = excluded.stream().filter(r -> r.getAge() < 0).count();
        long leadAnomalyCount = excluded.stream().filter(r -> r.getLeadDays() < 0).count();

        assertThat(ageAnomalyCount).isEqualTo(1);
        assertThat(leadAnomalyCount).isEqualTo(2);
    }

    @Test
    @DisplayName("Chronological 80:20 split preserves chronological order without data leakage")
    void testChronologicalSplit(@TempDir Path tempDir) {
        DataPreprocessor preprocessor = new DataPreprocessor();
        File cleanCsv = tempDir.resolve("clean.csv").toFile();
        File reportMd = tempDir.resolve("report.md").toFile();
        preprocessor.cleanDataset(sampleCsvFile, cleanCsv, reportMd);

        DataPreprocessor.PreprocessedData split = preprocessor.splitChronologically(cleanCsv, 0.80);

        assertThat(split.getTrainCount()).isEqualTo(160);
        assertThat(split.getTestCount()).isEqualTo(40);
        assertThat(split.getTrainCount() + split.getTestCount()).isEqualTo(200);

        // Verify chronological separation: latest train ScheduledDay <= earliest test ScheduledDay
        String latestTrainSched = split.getTrainRecords().get(split.getTrainCount() - 1).get("ScheduledDay");
        String earliestTestSched = split.getTestRecords().get(0).get("ScheduledDay");

        assertThat(latestTrainSched.compareTo(earliestTestSched)).isLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName("ZeroR baseline predicts majority class and yields zero recall on positive class")
    void testZeroRBaseline(@TempDir Path tempDir) throws Exception {
        DataPreprocessor preprocessor = new DataPreprocessor();
        File cleanCsv = tempDir.resolve("clean.csv").toFile();
        File reportMd = tempDir.resolve("report.md").toFile();
        preprocessor.cleanDataset(sampleCsvFile, cleanCsv, reportMd);

        DataPreprocessor.PreprocessedData split = preprocessor.splitChronologically(cleanCsv, 0.80);

        ZeroR zeroR = new ZeroR();
        zeroR.buildClassifier(split.getTrainInstances());

        ModelEvaluator evaluator = new ModelEvaluator();
        EvaluationResult result = evaluator.evaluate(zeroR, split.getTestInstances(), "ZeroR");

        assertThat(result.getAccuracy()).isGreaterThan(0.50);
        assertThat(result.getRecall()).isEqualTo(0.0);
        assertThat(result.getF1Score()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("RandomForest trains on synthetic fixture and generates valid probabilities in [0, 1] summing to 1.0")
    void testRandomForestProbabilities(@TempDir Path tempDir) throws Exception {
        DataPreprocessor preprocessor = new DataPreprocessor();
        File cleanCsv = tempDir.resolve("clean.csv").toFile();
        File reportMd = tempDir.resolve("report.md").toFile();
        preprocessor.cleanDataset(sampleCsvFile, cleanCsv, reportMd);

        DataPreprocessor.PreprocessedData split = preprocessor.splitChronologically(cleanCsv, 0.80);

        RandomForest rf = new RandomForest();
        rf.setNumIterations(10);
        rf.setSeed(42);
        rf.buildClassifier(split.getTrainInstances());

        NoShowPredictor predictor = new NoShowPredictor(rf);

        Patient patient = new Patient(101L, "Sample", Gender.M, 35);
        Doctor doctor = new Doctor(1L, "Dr. House", Gender.M, 50, "Diagnostics", "101");
        Appointment appt = new Appointment(1001L, patient, doctor,
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDate.of(2026, 6, 15),
                "JARDIM DA PENHA");

        PredictionResult pred = predictor.predict(appt);

        assertThat(pred.isModelAvailable()).isTrue();
        assertThat(pred.getAttendanceProbability()).isBetween(0.0, 1.0);
        assertThat(pred.getNoShowProbability()).isBetween(0.0, 1.0);
        assertThat(pred.getAttendanceProbability() + pred.getNoShowProbability())
                .isCloseTo(1.0, within(0.001));
        assertThat(pred.getRiskCategory()).isNotNull();
        assertThat(pred.getSuggestedAction()).isNotNull();
    }

    @Test
    @DisplayName("ModelRepository serializes, saves, and reloads model intact")
    void testModelSerializationAndReload(@TempDir Path tempDir) throws Exception {
        FeatureExtractor extractor = new FeatureExtractor();
        Instances header = extractor.getDatasetHeader();

        // Create minimal dummy training instances
        Instances dummy = new Instances(header, 2);
        dummy.add(extractor.extractInstance(new Appointment(1L, new Patient(1L, "A", Gender.F, 20),
                new Doctor(1L, "D", Gender.M, 40, "Gen", "1"),
                LocalDateTime.of(2026, 1, 1, 9, 0), LocalDate.of(2026, 1, 1), "C")));
        dummy.instance(0).setClassValue("NO");

        dummy.add(extractor.extractInstance(new Appointment(2L, new Patient(2L, "B", Gender.M, 50),
                new Doctor(1L, "D", Gender.M, 40, "Gen", "1"),
                LocalDateTime.of(2026, 1, 1, 9, 0), LocalDate.of(2026, 1, 20), "C")));
        dummy.instance(1).setClassValue("YES");

        RandomForest originalRf = new RandomForest();
        originalRf.setNumIterations(5);
        originalRf.buildClassifier(dummy);

        File modelFile = tempDir.resolve("test.model").toFile();
        File metaFile = tempDir.resolve("test-meta.ser").toFile();

        ModelRepository repo = new ModelRepository();
        repo.saveModel(originalRf, modelFile);

        ModelMetadata meta = new ModelMetadata("1.0", "RF", "2026-11-22", 2, 2, 0,
                extractor.getFeatureNames(), "noShow", List.of("NO", "YES"), 5, 0, 42,
                1.0, 1.0, 1.0, 1.0, 1.0);
        repo.saveMetadata(meta, metaFile);

        assertThat(modelFile).exists().isNotEmpty();
        assertThat(metaFile).exists().isNotEmpty();

        // Reload
        weka.classifiers.Classifier reloadedClassifier = repo.loadModel(modelFile);
        ModelMetadata reloadedMeta = repo.loadMetadata(metaFile);

        assertThat(reloadedClassifier).isNotNull();
        assertThat(reloadedMeta.getModelVersion()).isEqualTo("1.0");

        // Verify inference with reloaded classifier
        NoShowPredictor predictor = new NoShowPredictor(reloadedClassifier);
        Appointment testAppt = new Appointment(3L, new Patient(3L, "C", Gender.F, 30),
                new Doctor(1L, "D", Gender.M, 40, "Gen", "1"),
                LocalDateTime.of(2026, 1, 1, 9, 0), LocalDate.of(2026, 1, 10), "C");

        PredictionResult pred = predictor.predict(testAppt);
        assertThat(pred.isModelAvailable()).isTrue();
        assertThat(pred.getNoShowProbability()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("WekaNoShowPredictionService handles missing model file gracefully")
    void testServiceMissingModelHandling(@TempDir Path tempDir) {
        File missingModel = tempDir.resolve("non_existent.model").toFile();
        File missingMeta = tempDir.resolve("non_existent.ser").toFile();

        NoShowPredictionService service = new WekaNoShowPredictionService(new ModelRepository(), missingModel, missingMeta);

        assertThat(service.isModelAvailable()).isFalse();

        Patient patient = new Patient(101L, "Test", Gender.F, 25);
        Doctor doctor = new Doctor(1L, "Doc", Gender.M, 45, "Gen", "1");
        Appointment appt = new Appointment(1L, patient, doctor, LocalDateTime.now(), LocalDate.now().plusDays(5), "C");

        PredictionResult result = service.predict(appt);
        assertThat(result.isModelAvailable()).isFalse();
        assertThat(result.getStatusMessage()).contains("ML MODEL NOT TRAINED OR AVAILABLE");
        assertThat(result.getPredictedClass()).isEqualTo("UNAVAILABLE");
    }
}
