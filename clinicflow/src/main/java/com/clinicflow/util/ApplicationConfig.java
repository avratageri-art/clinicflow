package com.clinicflow.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized application configuration.
 * Manages storage locations, ML hyperparameters, risk thresholds, and split ratios.
 * Supports optional overrides via OS environment variables or a local .env file.
 */
public final class ApplicationConfig {

    private static final Logger LOGGER = Logger.getLogger(ApplicationConfig.class.getName());
    private static final ApplicationConfig INSTANCE = new ApplicationConfig();

    // Data Directory Structure
    private final Path projectRoot;
    private final Path rawDataPath;
    private final Path processedDataPath;
    private final Path appDataPath;
    private final Path modelsPath;
    private final Path reportsPath;

    // ML Strategy Thresholds
    private final double trainTestSplitRatio;
    private final double lowRiskThreshold;
    private final double highRiskThreshold;

    private ApplicationConfig() {
        this(Paths.get("").toAbsolutePath().normalize(), loadEnvWithDotEnv(Paths.get("").toAbsolutePath().normalize()));
    }

    /**
     * Constructs an ApplicationConfig from an explicit working directory and environment map.
     * Useful for testing configuration resolution and threshold overrides.
     */
    public ApplicationConfig(Path workingDir, Map<String, String> env) {
        this.projectRoot = resolveProjectRoot(workingDir, env);
        Path writableRoot = Files.isWritable(projectRoot) ? projectRoot : Paths.get(System.getProperty("java.io.tmpdir"), "clinicflow");
        this.rawDataPath = projectRoot.resolve("data").resolve("raw").resolve("appointments.csv");
        this.processedDataPath = writableRoot.resolve("data").resolve("processed");
        this.appDataPath = writableRoot.resolve("data").resolve("app");
        this.modelsPath = writableRoot.resolve("models");
        this.reportsPath = writableRoot.resolve("reports");

        double split = parseDoubleOrDefault(env.get("CLINICFLOW_TRAIN_TEST_SPLIT"), 0.80, "CLINICFLOW_TRAIN_TEST_SPLIT");
        this.trainTestSplitRatio = (split >= 0.10 && split <= 0.95) ? split : 0.80;

        double low = parseDoubleOrDefault(env.get("CLINICFLOW_LOW_RISK_THRESHOLD"), 0.20, "CLINICFLOW_LOW_RISK_THRESHOLD");
        double high = parseDoubleOrDefault(env.get("CLINICFLOW_HIGH_RISK_THRESHOLD"), 0.50, "CLINICFLOW_HIGH_RISK_THRESHOLD");
        if (low > 0.0 && high < 1.0 && low < high) {
            this.lowRiskThreshold = low;
            this.highRiskThreshold = high;
        } else {
            this.lowRiskThreshold = 0.20;
            this.highRiskThreshold = 0.50;
        }
    }

    static Path resolveProjectRoot(Path workingDir, Map<String, String> env) {
        String homeOverride = env.get("CLINICFLOW_HOME");
        if (homeOverride != null && !homeOverride.isBlank()) {
            Path overridePath = Paths.get(homeOverride.trim());
            if (!overridePath.isAbsolute()) {
                overridePath = workingDir.resolve(overridePath).normalize();
            }
            if (Files.isDirectory(overridePath)) {
                return overridePath;
            }
            LOGGER.log(Level.WARNING, "CLINICFLOW_HOME does not exist: {0}; falling back to auto-detection.", overridePath);
        }

        if (Files.exists(workingDir.resolve("pom.xml")) || Files.isDirectory(workingDir.resolve("data"))) {
            return workingDir;
        }
        Path nestedClinicFlow = workingDir.resolve("clinicflow");
        if (Files.exists(nestedClinicFlow.resolve("pom.xml")) || Files.isDirectory(nestedClinicFlow.resolve("data"))) {
            return nestedClinicFlow;
        }
        return workingDir;
    }

    static Map<String, String> loadEnvWithDotEnv(Path workingDir) {
        Map<String, String> merged = new HashMap<>();
        Path[] candidates = {
                workingDir.resolve(".env"),
                workingDir.getParent() != null ? workingDir.getParent().resolve(".env") : null
        };
        for (Path candidate : candidates) {
            if (candidate != null && Files.isRegularFile(candidate)) {
                readDotEnvFile(candidate, merged);
                break;
            }
        }
        // OS environment variables take precedence over .env
        merged.putAll(System.getenv());
        return merged;
    }

    private static void readDotEnvFile(Path envFile, Map<String, String> target) {
        try {
            for (String rawLine : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx <= 0) {
                    continue;
                }
                String key = line.substring(0, eqIdx).trim();
                String val = line.substring(eqIdx + 1).trim();
                if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                    val = val.substring(1, val.length() - 1);
                }
                target.put(key, val);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to read .env file at " + envFile, e);
        }
    }

    private static double parseDoubleOrDefault(String raw, double defaultValue, String key) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "Invalid numeric value for {0}: ''{1}''; using default {2}.",
                    new Object[]{key, raw, defaultValue});
            return defaultValue;
        }
    }

    public static ApplicationConfig getInstance() {
        return INSTANCE;
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public Path getRawDataPath() {
        return rawDataPath;
    }

    /**
     * Returns the primary raw dataset if present, otherwise falls back to the
     * compact self-contained sample dataset so the app runs out-of-the-box.
     */
    public Path getEffectiveRawDataPath() {
        if (Files.isRegularFile(rawDataPath)) {
            return rawDataPath;
        }
        return projectRoot.resolve("data").resolve("raw").resolve("sample_appointments.csv");
    }

    public Path getProcessedDataPath() {
        return processedDataPath;
    }

    public Path getAppDataPath() {
        return appDataPath;
    }

    public Path getModelsPath() {
        return modelsPath;
    }

    public Path getReportsPath() {
        return reportsPath;
    }

    public double getTrainTestSplitRatio() {
        return trainTestSplitRatio;
    }

    public double getLowRiskThreshold() {
        return lowRiskThreshold;
    }

    public double getHighRiskThreshold() {
        return highRiskThreshold;
    }
}
