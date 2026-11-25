package com.clinicflow.ml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Value object encapsulating classification metrics evaluated on a test dataset.
 */
public class EvaluationResult {

    private final String modelName;
    private final int totalTestInstances;
    private final double accuracy;
    private final double precision;  // for positive class (YES)
    private final double recall;     // for positive class (YES)
    private final double f1Score;    // for positive class (YES)
    private final double rocArea;    // ROC-AUC for positive class
    private final long truePositives;   // Actual YES, Predicted YES
    private final long falsePositives;  // Actual NO, Predicted YES
    private final long trueNegatives;   // Actual NO, Predicted NO
    private final long falseNegatives;  // Actual YES, Predicted NO

    public EvaluationResult(String modelName, int totalTestInstances,
                            double accuracy, double precision, double recall,
                            double f1Score, double rocArea,
                            long truePositives, long falsePositives,
                            long trueNegatives, long falseNegatives) {
        this.modelName = modelName;
        this.totalTestInstances = totalTestInstances;
        this.accuracy = accuracy;
        this.precision = precision;
        this.recall = recall;
        this.f1Score = f1Score;
        this.rocArea = rocArea;
        this.truePositives = truePositives;
        this.falsePositives = falsePositives;
        this.trueNegatives = trueNegatives;
        this.falseNegatives = falseNegatives;
    }

    public String getModelName() { return modelName; }
    public int getTotalTestInstances() { return totalTestInstances; }
    public double getAccuracy() { return accuracy; }
    public double getPrecision() { return precision; }
    public double getRecall() { return recall; }
    public double getF1Score() { return f1Score; }
    public double getRocArea() { return rocArea; }
    public long getTruePositives() { return truePositives; }
    public long getFalsePositives() { return falsePositives; }
    public long getTrueNegatives() { return trueNegatives; }
    public long getFalseNegatives() { return falseNegatives; }

    public void saveConfusionMatrixCsv(File destination) throws IOException {
        destination.getParentFile().mkdirs();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(destination), StandardCharsets.UTF_8))) {
            writer.println("Actual\\Predicted,Predicted_NO(Attended),Predicted_YES(NoShow),Total_Actual");
            writer.printf("Actual_NO(Attended),%d,%d,%d%n", trueNegatives, falsePositives, trueNegatives + falsePositives);
            writer.printf("Actual_YES(NoShow),%d,%d,%d%n", falseNegatives, truePositives, falseNegatives + truePositives);
            writer.printf("Total_Predicted,%d,%d,%d%n", trueNegatives + falseNegatives, falsePositives + truePositives, totalTestInstances);
        }
    }

    @Override
    public String toString() {
        return String.format("%s: Accuracy=%.2f%%, Precision(YES)=%.4f, Recall(YES)=%.4f, F1(YES)=%.4f, ROC-AUC=%.4f [TP=%d, FP=%d, TN=%d, FN=%d]",
                modelName, accuracy * 100.0, precision, recall, f1Score, rocArea,
                truePositives, falsePositives, trueNegatives, falseNegatives);
    }
}
