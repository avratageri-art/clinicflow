# Changelog

All notable changes to **ClinicFlow** are documented in this file.

## [1.0.0] - 2026-11-25

### Added
- **Repository & Documentation**:
  - Root `README.md`, `LICENSE` (MIT), `CONTRIBUTING.md`, `SECURITY.md`, `.gitignore`, `.env.example`, and `docs/ARCHITECTURE_DIAGRAMS.md` with Mermaid diagrams.
  - Cross-platform `run-clinicflow.sh` launcher alongside updated `clinicflow/run-clinicflow.bat`.
- **Configuration & Portability**:
  - Environment variable and `.env` support in `ApplicationConfig` (`CLINICFLOW_HOME`, `CLINICFLOW_TRAIN_TEST_SPLIT`, `CLINICFLOW_LOW_RISK_THRESHOLD`, `CLINICFLOW_HIGH_RISK_THRESHOLD`).
  - Automatic detection of `clinicflow/` subdirectory when launched from the repository root.
- **ML & UI Enhancements**:
  - Dynamic confusion matrix loading from `reports/confusion-matrix.csv` in `ModelInsightsService`.
  - In-app asynchronous **Train / Retrain Model** button in `ModelInsightsPanel` so fresh clones without the 234 MB `.model` file can train directly from the GUI.
- **Core Platform (Existing)**:
  - Java 17 domain model (`Person`, `Patient`, `Doctor`, `Appointment`, `ReminderRecord`).
  - Atomic CSV repositories and in-memory test repositories.
  - Weka 3.8.6 ML pipeline (`DataPreprocessor`, `FeatureExtractor`, `NoShowModelTrainer`, `ModelEvaluator`, `NoShowPredictor`) with chronological 80:20 split and `ClassBalancer` + 100-tree `RandomForest`.
  - 8-module Java Swing desktop application (`Dashboard`, `Patients`, `Doctors`, `Appointments`, `Queue Management`, `Reminder Management`, `ML Model Insights`, `About / Help`).
  - Automated JUnit 5 + AssertJ test suite.
