package com.clinicflow.ml;

import com.clinicflow.util.ClinicFlowException;
import weka.classifiers.Classifier;
import weka.core.SerializationHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Handles persistence and retrieval of trained Weka models and associated metadata.
 */
public class ModelRepository {

    /**
     * Saves a trained Weka classifier to disk using standard Weka serialization.
     */
    public void saveModel(Classifier classifier, File modelFile) {
        if (classifier == null) {
            throw new IllegalArgumentException("Classifier cannot be null");
        }
        modelFile.getParentFile().mkdirs();
        try {
            SerializationHelper.write(modelFile.getAbsolutePath(), classifier);
        } catch (Exception e) {
            throw new ClinicFlowException("Failed to serialize Weka model to: " + modelFile.getAbsolutePath(), e);
        }
    }

    /**
     * Saves model metadata to disk.
     */
    public void saveMetadata(ModelMetadata metadata, File metadataFile) {
        if (metadata == null) {
            throw new IllegalArgumentException("Metadata cannot be null");
        }
        metadataFile.getParentFile().mkdirs();

        // 1. Save Java serialized object
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(metadataFile))) {
            oos.writeObject(metadata);
        } catch (Exception e) {
            throw new ClinicFlowException("Failed to save model metadata: " + metadataFile.getAbsolutePath(), e);
        }

        // 2. Also save human-readable text metadata file alongside
        File txtFile = new File(metadataFile.getParentFile(), "model-metadata.txt");
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(txtFile), StandardCharsets.UTF_8))) {
            pw.println("ClinicFlow Model Metadata");
            pw.println("=========================");
            pw.println("Version: " + metadata.getModelVersion());
            pw.println("Algorithm: " + metadata.getAlgorithmName());
            pw.println("Trained: " + metadata.getTrainingTimestamp());
            pw.println("Total Cleaned Records: " + metadata.getTotalCleanedRecords());
            pw.println("Training Records (80%): " + metadata.getTrainingRecordCount());
            pw.println("Testing Records (20%): " + metadata.getTestingRecordCount());
            pw.println("Features: " + metadata.getFeatureNames());
            pw.println("Target: " + metadata.getTargetAttributeName());
            pw.println("Classes: " + metadata.getClassValues());
            pw.println("Trees: " + metadata.getNumTrees());
            pw.println("Seed: " + metadata.getRandomSeed());
            pw.println("Test Accuracy: " + String.format("%.2f%%", metadata.getTestAccuracy() * 100.0));
            pw.println("Test Precision (YES): " + String.format("%.4f", metadata.getTestPrecision()));
            pw.println("Test Recall (YES): " + String.format("%.4f", metadata.getTestRecall()));
            pw.println("Test F1-Score (YES): " + String.format("%.4f", metadata.getTestF1Score()));
            pw.println("Test ROC-AUC (YES): " + String.format("%.4f", metadata.getTestRocArea()));
        } catch (Exception ignored) {
        }
    }

    /**
     * Loads a serialized Weka classifier from disk.
     */
    public Classifier loadModel(File modelFile) {
        if (modelFile == null || !modelFile.exists()) {
            throw new ClinicFlowException("Model file does not exist: " +
                    (modelFile != null ? modelFile.getAbsolutePath() : "null"));
        }
        try {
            return (Classifier) SerializationHelper.read(modelFile.getAbsolutePath());
        } catch (Exception e) {
            throw new ClinicFlowException("Failed to deserialize Weka model from: " + modelFile.getAbsolutePath(), e);
        }
    }

    /**
     * Loads model metadata from disk.
     */
    public ModelMetadata loadMetadata(File metadataFile) {
        if (metadataFile == null || !metadataFile.exists()) {
            throw new ClinicFlowException("Metadata file does not exist: " +
                    (metadataFile != null ? metadataFile.getAbsolutePath() : "null"));
        }
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(metadataFile))) {
            return (ModelMetadata) ois.readObject();
        } catch (Exception e) {
            throw new ClinicFlowException("Failed to read model metadata: " + metadataFile.getAbsolutePath(), e);
        }
    }
}
