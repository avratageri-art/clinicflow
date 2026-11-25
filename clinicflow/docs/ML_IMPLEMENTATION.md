# ClinicFlow — Machine Learning Implementation Specification

## 1. Machine Learning Engine Architecture

The ClinicFlow Machine Learning engine is implemented natively in Java 17 leveraging Weka 3.8.6. It performs supervised binary classification to estimate appointment no-show probability at booking time.

```
+-------------------------------------------------------------------------+
|                        Data Preprocessing Stage                         |
|  data/raw/appointments.csv (110,527 records)                            |
|       |                                                                 |
|       v                                                                 |
|  DataPreprocessor: Filters 6 anomalies (1 Age=-1, 5 Negative Leads)    |
|       |                                                                 |
|       v                                                                 |
|  data/processed/clean_appointments.csv (110,521 valid records)          |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
|                     Chronological Partitioning (80:20)                  |
|  Sorted by ScheduledDay (Zero Future Leakage into Past)                 |
|       |                                                                 |
|       +---> Training Partition (88,417 records, earliest 80%)           |
|       |                                                                 |
|       +---> Testing Partition  (22,104 records, latest 20%)             |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
|                       Model Training & Evaluation                       |
|  Candidate 1: ZeroR Baseline (Majority Class)                           |
|  Candidate 2: Standard RandomForest (100 trees, seed=42)                |
|  Candidate 3: Class-Balanced RandomForest (ClassBalancer on Train Only) |
|       |                                                                 |
|       v                                                                 |
|  Evaluation on Untouched Test Set -> Selected: Balanced RandomForest    |
|  F1(YES) = 0.2585 | Recall(YES) = 32.93% | ROC-AUC = 0.6658             |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
|                  Model Persistence & Live Integration                   |
|  Serialized to: models/no-show-random-forest.model                      |
|  Metadata to:   models/model-metadata.ser & model-metadata.txt          |
|  Runtime:       WekaNoShowPredictionService -> AppointmentService       |
+-------------------------------------------------------------------------+
```

---

## 2. Key Java Components

1. **`FeatureExtractor` (`com.clinicflow.ml`)**:
   - Maintains an exact 6-feature attribute schema + 1 target class.
   - Extracts identical features from both historical CSV records and newly instantiated `Appointment` domain objects.
   - Strictly ensures no identifier leakage (`PatientId`, `AppointmentID`) and no future leakage (`SMS_received`, `No-show`).

2. **`DataPreprocessor` (`com.clinicflow.ml`)**:
   - Parses ISO-8601 timestamps with timezone handling.
   - Audits and prunes 6 verifiable data entry anomalies without synthetic data generation.
   - Sorts records chronologically by `ScheduledDay` and generates Weka `Instances`.

3. **`NoShowModelTrainer` (`com.clinicflow.ml`)**:
   - Trains ZeroR, Standard RandomForest, and Balanced RandomForest.
   - Evaluates each candidate against the held-out test partition.
   - Automatically selects and serializes the optimal model.

4. **`ModelEvaluator` (`com.clinicflow.ml`)**:
   - Computes Accuracy, Precision, Recall, F1-Score, ROC-AUC, and Confusion Matrix.
   - Generates `reports/ml-evaluation.md`, `reports/confusion-matrix.csv`, `reports/test-predictions.csv`, and `reports/sample-predictions.md`.

5. **`ModelRepository` (`com.clinicflow.ml`)**:
   - Manages Java/Weka model serialization via `weka.core.SerializationHelper`.
   - Persists `ModelMetadata` containing model version, hyperparameters, features, and metrics.

6. **`NoShowPredictor` (`com.clinicflow.ml`)**:
   - Thread-safe runtime wrapper around the deserialized Weka classifier.
   - Invokes `classifier.distributionForInstance()` to extract genuine class probabilities.

7. **`WekaNoShowPredictionService` (`com.clinicflow.service`)**:
   - Implements `NoShowPredictionService`.
   - When active, attaches real ML predictions to booked appointments.
   - When uninitialized, explicitly reports `ML MODEL NOT TRAINED OR AVAILABLE`.
