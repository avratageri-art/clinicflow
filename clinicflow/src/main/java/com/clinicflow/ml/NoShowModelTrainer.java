package com.clinicflow.ml;

import com.clinicflow.util.ApplicationConfig;
import com.clinicflow.util.ClinicFlowException;
import org.apache.commons.csv.CSVRecord;
import weka.classifiers.Classifier;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.rules.ZeroR;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.filters.supervised.instance.ClassBalancer;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Orchestrates ML training, evaluation, comparison, and model serialization.
 * Implements:
 * 1. ZeroR baseline.
 * 2. Standard Weka RandomForest.
 * 3. Class-Balanced Weka RandomForest (training set rebalancing only).
 * Compares models on the untouched chronological test set and persists the superior classifier.
 */
public class NoShowModelTrainer {

    public static final String MODEL_VERSION = "2.0.0-Weka-RandomForest";
    public static final int NUM_TREES = 100;
    public static final int RANDOM_SEED = 42;
    public static final int MAX_DEPTH = 0; // 0 = unlimited in Weka

    private final DataPreprocessor preprocessor;
    private final ModelEvaluator evaluator;
    private final ModelRepository repository;
    private final ApplicationConfig config;

    public NoShowModelTrainer() {
        this.preprocessor = new DataPreprocessor();
        this.evaluator = new ModelEvaluator();
        this.repository = new ModelRepository();
        this.config = ApplicationConfig.getInstance();
    }

    public NoShowModelTrainer(DataPreprocessor preprocessor, ModelEvaluator evaluator,
                              ModelRepository repository, ApplicationConfig config) {
        this.preprocessor = preprocessor;
        this.evaluator = evaluator;
        this.repository = repository;
        this.config = config;
    }

    /**
     * Executes the complete training and evaluation pipeline.
     */
    public TrainingPipelineResult runPipeline() {
        File rawCsv = config.getEffectiveRawDataPath().toFile();
        File cleanCsv = config.getProcessedDataPath().resolve("clean_appointments.csv").toFile();
        File cleaningReport = config.getReportsPath().resolve("data-cleaning-report.md").toFile();

        // 1. Clean dataset
        System.out.println("[ML Pipeline] Step 1: Cleaning raw dataset and verifying entry anomalies...");
        CleaningSummary cleaningSummary = preprocessor.cleanDataset(rawCsv, cleanCsv, cleaningReport);
        System.out.printf("  - Original: %,d | Excluded: %d | Clean: %,d\n",
                cleaningSummary.getOriginalRecordCount(),
                cleaningSummary.getExcludedRecordCount(),
                cleaningSummary.getValidRecordCount());

        // 2. Chronological 80:20 split
        System.out.println("[ML Pipeline] Step 2: Performing chronological 80:20 train/test split...");
        DataPreprocessor.PreprocessedData split = preprocessor.splitChronologically(cleanCsv, config.getTrainTestSplitRatio());
        Instances trainData = split.getTrainInstances();
        Instances testData = split.getTestInstances();
        System.out.printf("  - Training Records: %,d (80%%)\n", trainData.numInstances());
        System.out.printf("  - Testing Records: %,d (20%%)\n", testData.numInstances());

        // 3. Train Baseline: ZeroR
        System.out.println("[ML Pipeline] Step 3: Training ZeroR baseline on training data...");
        ZeroR zeroR = new ZeroR();
        try {
            zeroR.buildClassifier(trainData);
        } catch (Exception e) {
            throw new ClinicFlowException("Failed to build ZeroR baseline", e);
        }
        EvaluationResult zeroRResult = evaluator.evaluate(zeroR, testData, "ZeroR (Baseline)");
        System.out.printf("  - ZeroR Accuracy: %.2f%% | Recall(YES): %.4f | F1(YES): %.4f\n",
                zeroRResult.getAccuracy() * 100.0, zeroRResult.getRecall(), zeroRResult.getF1Score());

        // 4. Train Model A: Standard RandomForest
        System.out.println("[ML Pipeline] Step 4: Training Standard RandomForest (100 trees, seed=42)...");
        RandomForest standardRf = new RandomForest();
        standardRf.setNumIterations(NUM_TREES);
        standardRf.setSeed(RANDOM_SEED);
        standardRf.setMaxDepth(MAX_DEPTH);
        try {
            standardRf.buildClassifier(trainData);
        } catch (Exception e) {
            throw new ClinicFlowException("Failed to train Standard RandomForest", e);
        }
        EvaluationResult standardRfResult = evaluator.evaluate(standardRf, testData, "Standard RandomForest");
        System.out.printf("  - Standard RF Accuracy: %.2f%% | Precision: %.4f | Recall: %.4f | F1: %.4f | AUC: %.4f\n",
                standardRfResult.getAccuracy() * 100.0, standardRfResult.getPrecision(),
                standardRfResult.getRecall(), standardRfResult.getF1Score(), standardRfResult.getRocArea());

        // 5. Train Model B: Class-Balanced RandomForest
        System.out.println("[ML Pipeline] Step 5: Training Class-Balanced RandomForest (ClassBalancer on train only)...");
        FilteredClassifier balancedRf = new FilteredClassifier();
        ClassBalancer balancer = new ClassBalancer();
        RandomForest innerRf = new RandomForest();
        innerRf.setNumIterations(NUM_TREES);
        innerRf.setSeed(RANDOM_SEED);
        innerRf.setMaxDepth(MAX_DEPTH);
        balancedRf.setFilter(balancer);
        balancedRf.setClassifier(innerRf);
        try {
            balancedRf.buildClassifier(trainData);
        } catch (Exception e) {
            throw new ClinicFlowException("Failed to train Balanced RandomForest", e);
        }
        EvaluationResult balancedRfResult = evaluator.evaluate(balancedRf, testData, "Balanced RandomForest");
        System.out.printf("  - Balanced RF Accuracy: %.2f%% | Precision: %.4f | Recall: %.4f | F1: %.4f | AUC: %.4f\n",
                balancedRfResult.getAccuracy() * 100.0, balancedRfResult.getPrecision(),
                balancedRfResult.getRecall(), balancedRfResult.getF1Score(), balancedRfResult.getRocArea());

        // 6. Select Best Model
        // We select based on F1-Score / Recall on No-Show class while preserving strong AUC
        Classifier selectedModel;
        EvaluationResult selectedResult;
        if (balancedRfResult.getF1Score() > standardRfResult.getF1Score()) {
            selectedModel = balancedRf;
            selectedResult = balancedRfResult;
        } else {
            selectedModel = standardRf;
            selectedResult = standardRfResult;
        }
        System.out.println("  - Selected Optimal Model: " + selectedResult.getModelName() +
                " (F1=" + String.format("%.4f", selectedResult.getF1Score()) + ", AUC=" + String.format("%.4f", selectedResult.getRocArea()) + ")");

        // 7. Save Model & Metadata
        System.out.println("[ML Pipeline] Step 6: Persisting trained model and metadata to models/...");
        File modelFile = config.getModelsPath().resolve("no-show-random-forest.model").toFile();
        File metadataFile = config.getModelsPath().resolve("model-metadata.ser").toFile();
        repository.saveModel(selectedModel, modelFile);

        FeatureExtractor extractor = new FeatureExtractor();
        ModelMetadata metadata = new ModelMetadata(
                MODEL_VERSION,
                selectedResult.getModelName(),
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                cleaningSummary.getValidRecordCount(),
                trainData.numInstances(),
                testData.numInstances(),
                extractor.getFeatureNames(),
                extractor.getTargetAttributeName(),
                extractor.getClassValues(),
                NUM_TREES,
                MAX_DEPTH,
                RANDOM_SEED,
                selectedResult.getAccuracy(),
                selectedResult.getPrecision(),
                selectedResult.getRecall(),
                selectedResult.getF1Score(),
                selectedResult.getRocArea()
        );
        repository.saveMetadata(metadata, metadataFile);

        // 8. Export Evaluation Reports & Predictions
        System.out.println("[ML Pipeline] Step 7: Generating evaluation reports and prediction files...");
        File evalReport = config.getReportsPath().resolve("ml-evaluation.md").toFile();
        File cmCsv = config.getReportsPath().resolve("confusion-matrix.csv").toFile();
        File predCsv = config.getReportsPath().resolve("test-predictions.csv").toFile();
        File sampleMd = config.getReportsPath().resolve("sample-predictions.md").toFile();

        try {
            evaluator.exportComparativeEvaluationReport(zeroRResult, standardRfResult, balancedRfResult, selectedResult, evalReport);
            selectedResult.saveConfusionMatrixCsv(cmCsv);
            evaluator.exportTestPredictionsCsv(selectedModel, testData, split.getTestRecords(), predCsv);
            evaluator.exportSamplePredictionsMarkdown(selectedModel, testData, split.getTestRecords(), sampleMd);
        } catch (Exception e) {
            throw new ClinicFlowException("Failed to export evaluation outputs", e);
        }

        System.out.println("  - Generated reports/ml-evaluation.md");
        System.out.println("  - Generated reports/confusion-matrix.csv");
        System.out.println("  - Generated reports/test-predictions.csv");
        System.out.println("  - Generated reports/sample-predictions.md");

        return new TrainingPipelineResult(cleaningSummary, zeroRResult, standardRfResult, balancedRfResult, selectedResult, metadata);
    }

    public static class TrainingPipelineResult {
        private final CleaningSummary cleaningSummary;
        private final EvaluationResult zeroRResult;
        private final EvaluationResult standardRfResult;
        private final EvaluationResult balancedRfResult;
        private final EvaluationResult selectedResult;
        private final ModelMetadata metadata;

        public TrainingPipelineResult(CleaningSummary cleaningSummary,
                                      EvaluationResult zeroRResult,
                                      EvaluationResult standardRfResult,
                                      EvaluationResult balancedRfResult,
                                      EvaluationResult selectedResult,
                                      ModelMetadata metadata) {
            this.cleaningSummary = cleaningSummary;
            this.zeroRResult = zeroRResult;
            this.standardRfResult = standardRfResult;
            this.balancedRfResult = balancedRfResult;
            this.selectedResult = selectedResult;
            this.metadata = metadata;
        }

        public CleaningSummary getCleaningSummary() { return cleaningSummary; }
        public EvaluationResult getZeroRResult() { return zeroRResult; }
        public EvaluationResult getStandardRfResult() { return standardRfResult; }
        public EvaluationResult getBalancedRfResult() { return balancedRfResult; }
        public EvaluationResult getSelectedResult() { return selectedResult; }
        public ModelMetadata getMetadata() { return metadata; }
    }
}
