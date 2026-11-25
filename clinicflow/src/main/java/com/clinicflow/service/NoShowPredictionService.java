package com.clinicflow.service;

import com.clinicflow.ml.ModelMetadata;
import com.clinicflow.model.Appointment;
import com.clinicflow.model.PredictionResult;

import java.util.Optional;

/**
 * Service contract for patient appointment no-show predictions.
 * Provides clean architectural abstraction between application workflows and the ML engine.
 */
public interface NoShowPredictionService {

    /**
     * Checks whether a trained ML model is loaded and ready for live inference.
     */
    boolean isModelAvailable();

    /**
     * Generates a prediction for an appointment.
     * If the model is not loaded, returns an explicit unavailable result.
     */
    PredictionResult predict(Appointment appointment);

    /**
     * Returns metadata of the active model, if loaded.
     */
    Optional<ModelMetadata> getActiveModelMetadata();

    /**
     * Attempts to load or reload the model from disk.
     */
    boolean reloadModel();
}
