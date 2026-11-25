package com.clinicflow.ml;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores comprehensive metadata regarding the trained Weka model.
 * Persisted alongside the serialized classifier.
 */
public class ModelMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String modelVersion;
    private final String algorithmName;
    private final String trainingTimestamp;
    private final int totalCleanedRecords;
    private final int trainingRecordCount;
    private final int testingRecordCount;
    private final List<String> featureNames;
    private final String targetAttributeName;
    private final List<String> classValues;
    private final int numTrees;
    private final int maxDepth;
    private final int randomSeed;
    private final double testAccuracy;
    private final double testPrecision;
    private final double testRecall;
    private final double testF1Score;
    private final double testRocArea;

    public ModelMetadata(String modelVersion, String algorithmName,
                         String trainingTimestamp, int totalCleanedRecords,
                         int trainingRecordCount, int testingRecordCount,
                         List<String> featureNames, String targetAttributeName,
                         List<String> classValues, int numTrees, int maxDepth,
                         int randomSeed, double testAccuracy, double testPrecision,
                         double testRecall, double testF1Score, double testRocArea) {
        this.modelVersion = modelVersion;
        this.algorithmName = algorithmName;
        this.trainingTimestamp = trainingTimestamp;
        this.totalCleanedRecords = totalCleanedRecords;
        this.trainingRecordCount = trainingRecordCount;
        this.testingRecordCount = testingRecordCount;
        this.featureNames = new ArrayList<>(featureNames);
        this.targetAttributeName = targetAttributeName;
        this.classValues = new ArrayList<>(classValues);
        this.numTrees = numTrees;
        this.maxDepth = maxDepth;
        this.randomSeed = randomSeed;
        this.testAccuracy = testAccuracy;
        this.testPrecision = testPrecision;
        this.testRecall = testRecall;
        this.testF1Score = testF1Score;
        this.testRocArea = testRocArea;
    }

    public String getModelVersion() { return modelVersion; }
    public String getAlgorithmName() { return algorithmName; }
    public String getTrainingTimestamp() { return trainingTimestamp; }
    public int getTotalCleanedRecords() { return totalCleanedRecords; }
    public int getTrainingRecordCount() { return trainingRecordCount; }
    public int getTestingRecordCount() { return testingRecordCount; }
    public List<String> getFeatureNames() { return Collections.unmodifiableList(featureNames); }
    public String getTargetAttributeName() { return targetAttributeName; }
    public List<String> getClassValues() { return Collections.unmodifiableList(classValues); }
    public int getNumTrees() { return numTrees; }
    public int getMaxDepth() { return maxDepth; }
    public int getRandomSeed() { return randomSeed; }
    public double getTestAccuracy() { return testAccuracy; }
    public double getTestPrecision() { return testPrecision; }
    public double getTestRecall() { return testRecall; }
    public double getTestF1Score() { return testF1Score; }
    public double getTestRocArea() { return testRocArea; }

    @Override
    public String toString() {
        return "ModelMetadata{" +
                "version='" + modelVersion + '\'' +
                ", algorithm='" + algorithmName + '\'' +
                ", trained=" + trainingTimestamp +
                ", trainSize=" + trainingRecordCount +
                ", testSize=" + testingRecordCount +
                ", features=" + featureNames +
                ", accuracy=" + String.format("%.2f%%", testAccuracy * 100.0) +
                ", f1=" + String.format("%.4f", testF1Score) +
                '}';
    }
}
