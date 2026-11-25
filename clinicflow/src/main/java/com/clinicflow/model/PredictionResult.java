package com.clinicflow.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Value object encapsulating a genuine ML prediction result for an appointment.
 * Completely eliminates misleading heuristic fallback values.
 * Explicitly reports availability status when the ML model has not yet been loaded.
 */
public class PredictionResult implements Serializable {

    private static final long serialVersionUID = 2L;

    private final boolean modelAvailable;
    private final String predictedClass;
    private final double noShowProbability;
    private final double attendanceProbability;
    private final RiskCategory riskCategory;
    private final ReminderAction suggestedAction;
    private final String statusMessage;
    private final LocalDateTime generatedAt;

    /**
     * Constructs a genuine ML-driven prediction result.
     *
     * @param predictedClass       Predicted class ('YES' for No-Show, 'NO' for Attended)
     * @param noShowProbability    Estimated probability of patient no-show
     * @param attendanceProbability Estimated probability of patient attendance
     * @param riskCategory         Stratified risk category (LOW, MEDIUM, HIGH)
     * @param suggestedAction      Clinical reminder action
     */
    public PredictionResult(String predictedClass, double noShowProbability,
                            double attendanceProbability, RiskCategory riskCategory,
                            ReminderAction suggestedAction) {
        this.modelAvailable = true;
        this.predictedClass = predictedClass;
        this.noShowProbability = noShowProbability;
        this.attendanceProbability = attendanceProbability;
        this.riskCategory = riskCategory;
        this.suggestedAction = suggestedAction;
        this.statusMessage = "ML Model Prediction (Weka RandomForest)";
        this.generatedAt = LocalDateTime.now();
    }

    /**
     * Factory method creating an explicitly unpredicted state when the ML model is not available.
     * Prevents misleading rule-based estimates.
     */
    public static PredictionResult unavailable() {
        return new PredictionResult(
                false,
                "UNAVAILABLE",
                Double.NaN,
                Double.NaN,
                RiskCategory.LOW,
                ReminderAction.STANDARD,
                "ML MODEL NOT TRAINED OR AVAILABLE"
        );
    }

    /**
     * Factory method creating a prediction from computed class distribution probabilities.
     *
     * @param pNo  Probability of attendance (Class NO)
     * @param pYes Probability of no-show (Class YES)
     */
    public static PredictionResult fromProbabilities(double pNo, double pYes) {
        RiskCategory category = RiskCategory.fromProbability(pYes);
        // Aligned with Weka classifyInstance tie-breaking convention: strictly greater
        String predClass = (pYes > pNo) ? "YES" : "NO";
        ReminderAction action = ReminderAction.forRisk(category);
        return new PredictionResult(predClass, pYes, pNo, category, action);
    }

    private PredictionResult(boolean modelAvailable, String predictedClass,
                             double noShowProbability, double attendanceProbability,
                             RiskCategory riskCategory, ReminderAction suggestedAction,
                             String statusMessage) {
        this.modelAvailable = modelAvailable;
        this.predictedClass = predictedClass;
        this.noShowProbability = noShowProbability;
        this.attendanceProbability = attendanceProbability;
        this.riskCategory = riskCategory;
        this.suggestedAction = suggestedAction;
        this.statusMessage = statusMessage;
        this.generatedAt = LocalDateTime.now();
    }

    public boolean isModelAvailable() {
        return modelAvailable;
    }

    public String getPredictedClass() {
        return predictedClass;
    }

    public double getNoShowProbability() {
        return noShowProbability;
    }

    public double getAttendanceProbability() {
        return attendanceProbability;
    }

    public RiskCategory getRiskCategory() {
        return riskCategory;
    }

    public ReminderAction getSuggestedAction() {
        return suggestedAction;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    @Override
    public String toString() {
        if (!modelAvailable) {
            return "[" + statusMessage + "]";
        }
        return String.format("PredictionResult[Predicted: %s, NoShowProb: %.2f%%, AttendedProb: %.2f%%, Risk: %s, Action: %s]",
                predictedClass, noShowProbability * 100.0, attendanceProbability * 100.0, riskCategory, suggestedAction);
    }
}
