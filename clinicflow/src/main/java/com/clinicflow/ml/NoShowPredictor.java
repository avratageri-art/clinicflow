package com.clinicflow.ml;

import com.clinicflow.model.Appointment;
import com.clinicflow.model.PredictionResult;
import com.clinicflow.util.ClinicFlowException;
import weka.classifiers.Classifier;
import weka.core.Instance;

/**
 * Executes live probabilistic predictions for appointments using a loaded Weka classifier.
 * Thread-safe and stateless wrapper around the classifier and feature extractor.
 */
public class NoShowPredictor {

    private final Classifier classifier;
    private final FeatureExtractor featureExtractor;

    public NoShowPredictor(Classifier classifier) {
        if (classifier == null) {
            throw new IllegalArgumentException("Classifier cannot be null");
        }
        this.classifier = classifier;
        this.featureExtractor = new FeatureExtractor();
    }

    public NoShowPredictor(Classifier classifier, FeatureExtractor featureExtractor) {
        if (classifier == null) {
            throw new IllegalArgumentException("Classifier cannot be null");
        }
        this.classifier = classifier;
        this.featureExtractor = featureExtractor != null ? featureExtractor : new FeatureExtractor();
    }

    /**
     * Generates a genuine ML prediction for an appointment.
     *
     * @param appointment target appointment
     * @return structured prediction result with exact probabilities from Weka distribution
     */
    public PredictionResult predict(Appointment appointment) {
        if (appointment == null) {
            throw new IllegalArgumentException("Appointment cannot be null");
        }

        Instance instance = featureExtractor.extractInstance(appointment);
        double[] distribution;
        try {
            distribution = classifier.distributionForInstance(instance);
        } catch (Exception e) {
            throw new ClinicFlowException("Weka classifier failed to compute class distribution for appointment ID: " +
                    appointment.getAppointmentId(), e);
        }

        if (distribution == null || distribution.length < 2) {
            throw new ClinicFlowException("Invalid class distribution returned by Weka model for appointment ID: " +
                    appointment.getAppointmentId());
        }

        double pNo = distribution[0];   // Probability of Class NO (Attended)
        double pYes = distribution[1];  // Probability of Class YES (No-Show)

        return PredictionResult.fromProbabilities(pNo, pYes);
    }

    public Classifier getClassifier() {
        return classifier;
    }

    public FeatureExtractor getFeatureExtractor() {
        return featureExtractor;
    }
}
