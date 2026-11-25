package com.clinicflow.service;

import com.clinicflow.ml.ModelMetadata;
import com.clinicflow.ml.ModelRepository;
import com.clinicflow.ml.NoShowPredictor;
import com.clinicflow.model.Appointment;
import com.clinicflow.model.PredictionResult;
import com.clinicflow.util.ApplicationConfig;
import weka.classifiers.Classifier;

import java.io.File;
import java.util.Optional;

/**
 * Production implementation of {@link NoShowPredictionService} using Weka.
 * Loads the trained RandomForest model from models/no-show-random-forest.model.
 * When the model is absent or not yet trained, gracefully reports unavailable status
 * without resorting to misleading rule-based or hardcoded probabilities.
 */
public class WekaNoShowPredictionService implements NoShowPredictionService {

    private final ModelRepository repository;
    private final File modelFile;
    private final File metadataFile;

    private volatile NoShowPredictor predictor;
    private volatile ModelMetadata activeMetadata;

    public WekaNoShowPredictionService() {
        this(ApplicationConfig.getInstance());
    }

    public WekaNoShowPredictionService(ApplicationConfig config) {
        this(
                new ModelRepository(),
                config.getModelsPath().resolve("no-show-random-forest.model").toFile(),
                config.getModelsPath().resolve("model-metadata.ser").toFile()
        );
    }

    public WekaNoShowPredictionService(ModelRepository repository, File modelFile, File metadataFile) {
        this.repository = repository;
        this.modelFile = modelFile;
        this.metadataFile = metadataFile;
        reloadModel();
    }

    @Override
    public synchronized boolean isModelAvailable() {
        return predictor != null;
    }

    @Override
    public PredictionResult predict(Appointment appointment) {
        if (!isModelAvailable()) {
            return PredictionResult.unavailable();
        }
        return predictor.predict(appointment);
    }

    @Override
    public Optional<ModelMetadata> getActiveModelMetadata() {
        return Optional.ofNullable(activeMetadata);
    }

    @Override
    public synchronized boolean reloadModel() {
        if (modelFile.exists() && modelFile.length() > 0) {
            try {
                Classifier classifier = repository.loadModel(modelFile);
                this.predictor = new NoShowPredictor(classifier);

                if (metadataFile.exists() && metadataFile.length() > 0) {
                    try {
                        this.activeMetadata = repository.loadMetadata(metadataFile);
                    } catch (Exception ignored) {
                        this.activeMetadata = null;
                    }
                }
                return true;
            } catch (Exception e) {
                this.predictor = null;
                this.activeMetadata = null;
                return false;
            }
        } else {
            this.predictor = null;
            this.activeMetadata = null;
            return false;
        }
    }
}
