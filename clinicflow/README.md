# ClinicFlow: ML-Based Patient No-Show Prediction & Smart Queue Management

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/)
[![Build](https://img.shields.io/badge/Build-Maven-blue.svg)](https://maven.apache.org/)
[![Machine Learning](https://img.shields.io/badge/ML-Weka%203.8.6-green.svg)](https://www.cs.waikato.ac.nz/ml/weka/)
[![Dataset](https://img.shields.io/badge/Dataset-110%2C527%20Records-brightgreen.svg)](https://www.kaggle.com/datasets/joniarroba/noshowappointments/data)
[![Model](https://img.shields.io/badge/Model-RandomForest%20100%20Trees-brightgreen.svg)](models/model-metadata.txt)

A production-grade, academic Java application that addresses outpatient medical appointment no-shows using Machine Learning (Weka Random Forest) integrated directly into clinical appointment workflows.

---

## 1. Project Overview

Missed appointments disrupt clinic operations, waste valuable physician time, and lengthen wait queues. ClinicFlow provides clinical decision support by predicting the likelihood of an appointment no-show at booking time and recommending differentiated, non-punitive reminder actions:
* **Low Risk (<20%):** Standard automated SMS 24 hours prior.
* **Medium Risk (20%–50%):** Enhanced multi-channel SMS with direct confirmation links.
* **High Risk (>50%):** Dedicated front-desk phone outreach and queue backfill planning.

---

## 2. Technology Stack

* **Language:** Java 17 (Microsoft OpenJDK 17.0.12)
* **Build Tool:** Apache Maven 3.9.6
* **Machine Learning Engine:** Weka 3.8.6 (Java Native)
* **CSV Parsing:** Apache Commons CSV 1.10.0
* **Testing:** JUnit 5 (Jupiter 5.10.2) & AssertJ 3.25.3 (41 automated tests)
* **Desktop UI:** Java Swing (Professional custom design system, Segoe UI, Java2D charts)
* **Persistence:** Atomic CSV repositories (`data/app/`) with automated fallback

---

## 3. Project Directory Structure

```
clinicflow/
├── pom.xml                               # Maven build configuration
├── README.md                             # Project overview and instructions
├── run-clinicflow.bat                    # Windows one-click desktop launch script
├── .gitignore                            # Version control exclusions
├── data/
│   ├── raw/
│   │   └── appointments.csv              # Genuine Kaggle dataset (110,527 records, 10.74 MB)
│   ├── processed/
│   │   └── clean_appointments.csv        # Preprocessed valid records (110,521 records)
│   ├── app/                              # Operational appointment records & master data
│   │   ├── patients.csv                  # Persisted clinic patient directory
│   │   ├── doctors.csv                   # Persisted clinic doctor directory
│   │   ├── appointments.csv              # Persisted operational appointments
│   │   └── reminders.csv                 # Persisted audit trail of reminder dispatches
│   └── DATA_SOURCE.md                    # Attribution, provenance & schema dictionary
├── models/
│   ├── no-show-random-forest.model       # Serialized trained Weka RandomForest (100 trees, 233.9 MB)
│   ├── model-metadata.ser                # Serialized model metadata
│   └── model-metadata.txt                # Human-readable model metadata
├── reports/
│   ├── dataset-profile.md                # Computed statistical & quality inspection report
│   ├── data-cleaning-report.md           # Itemized audit of 6 excluded anomaly records
│   ├── ml-evaluation.md                  # Comprehensive ZeroR vs RandomForest evaluation report
│   ├── confusion-matrix.csv              # Exact confusion matrix on 22,104 test records
│   ├── test-predictions.csv              # Full itemized predictions for all 22,104 test instances
│   └── sample-predictions.md             # 5 genuine test prediction walkthroughs
├── docs/
│   ├── PROJECT_SPEC.md                   # Problem statement, clinical need, objectives, scope, I/O
│   ├── USER_GUIDE.md                     # Comprehensive Swing application user manual
│   ├── GUI_ARCHITECTURE.md              # Technical Swing architecture & concurrency model
│   ├── APPLICATION_WORKFLOW.md           # Complete operational workflows & sequence diagrams
│   ├── DATA_PERSISTENCE.md               # CSV schema definitions & atomic file writing strategy
│   ├── ML_DESIGN.md                      # Algorithm, feature selection, leakage audit, metrics
│   ├── ML_IMPLEMENTATION.md              # Technical Weka pipeline architecture
│   ├── FEATURE_ENGINEERING.md            # Feature schema, justifications, and leakage prevention
│   ├── TRAINING_TESTING.md               # Chronological 80:20 split and hyperparameter setup
│   ├── MODEL_EVALUATION.md               # In-depth metric analysis and class imbalance handling
│   ├── ML_EXPLANATION.md                 # Student-friendly explanation of ML and Random Forest
│   ├── SYSTEM_ARCHITECTURE.md            # Layered architecture diagram & component design
│   ├── JAVA_CONCEPTS.md                  # Comprehensive mapping of required Java OOP concepts
│   └── ASSIGNMENT_COMPLIANCE.md          # Professor's assignment compliance matrix
├── src/
│   ├── main/java/com/clinicflow/
│   │   ├── model/                        # Domain entities (Person, Patient, Doctor, Appointment, ReminderRecord, enums)
│   │   ├── repository/                   # Repositories (Generic Repo, Csv/InMemory Patient, Doctor, Appt, Reminder)
│   │   ├── service/                      # Services (PatientService, DoctorService, AppointmentService, ReminderService, ModelInsightsService)
│   │   ├── ml/                           # ML Engine (Preprocessor, Extractor, Trainer, Predictor, Evaluator)
│   │   ├── ui/                           # Java Swing GUI (ClinicFlowApp, Dashboard, Patient, Doctor, Appt, Queue, Reminder, Insights, About)
│   │   │   └── common/                   # UITheme, CardPanel, AttendanceChartComponent
│   │   ├── util/                         # Configuration and custom exceptions
│   │   └── Main.java                     # Application entry point (GUI default, CLI via --cli)
│   └── test/java/com/clinicflow/         # 41 automated unit and integration tests (100% pass)
```

---

## 4. Empirical Evaluation Results

Evaluated on the **untouched 20% chronological test partition** (22,104 appointments):

| Model | Accuracy | Precision (YES) | Recall (YES) | F1-Score (YES) | ROC-AUC | Status |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **ZeroR (Majority-Class Baseline)** | **88.09%** | 0.0000 | 0.0000 | 0.0000 | 0.5000 | Naive Baseline |
| **Standard Random Forest** | 84.34% | **0.2432** | 0.1489 | 0.1847 | **0.6660** | Candidate 1 |
| **Balanced Random Forest (Selected)** | 77.50% | 0.2128 | **0.3293** | **0.2585** | 0.6658 | **Selected Optimal Model** |

### Selected Confusion Matrix (`reports/confusion-matrix.csv`)
* **True Negatives (Attended correctly predicted):** 16,264
* **False Positives (Attended predicted as No-Show):** 3,207
* **False Negatives (No-Show predicted as Attended):** 1,766
* **True Positives (No-Show correctly predicted):** 867

---

## 5. Building and Running

### Prerequisites
* Java 17 (Microsoft OpenJDK or Eclipse Temurin)
* Apache Maven 3.8+

### Compile and Run Automated Unit Tests (41 Tests)
```bash
cd clinicflow
mvn clean test
```

### Build Executable Package
```bash
mvn clean package
```

### Launch the Java Swing Desktop Application
On Windows, double-click or run:
```cmd
run-clinicflow.bat
```
Or run directly via Maven:
```bash
mvn exec:java
```
Or execute the packaged JAR:
```bash
java -jar target/clinicflow-1.0.0-SNAPSHOT.jar
```

### Run Command-Line Demonstration (Headless / Verification Mode)
To run the automated verification CLI workflow without opening the GUI:
```bash
mvn exec:java -Dexec.args="--cli"
```
Or:
```bash
java -jar target/clinicflow-1.0.0-SNAPSHOT.jar --cli
```
*To force model retraining from raw data:*
```bash
mvn exec:java -Dexec.args="--retrain"
```

---

## 6. Academic Roadmap & Stage Boundaries

* **Stage 1 (Completed):** Project foundation, genuine dataset acquisition, deep dataset inspection, OOP domain modeling, repository & service layer, unit testing, and academic documentation.
* **Stage 2 (Completed):** Complete Weka ML engine, data preprocessing & anomaly pruning, chronological 80:20 train/test split, Random Forest training & evaluation, class imbalance handling, baseline comparison, model serialization/reload, and service integration.
* **Stage 3 (Completed):** Complete Java Swing desktop application (8 modules: Dashboard with Java2D charts, Patient Management, Doctor Management, Appointment Booking with live ML inference, Appointment Master Management, Smart Queue Management with FIFO fairness, Reminder Dispatch Simulation, ML Insights Dashboard), persistent CSV storage with atomic swapping, background non-blocking model loading, Windows launcher, and 41 comprehensive automated tests.
* **Stage 4 (Planned):** Final academic documentation, defense presentation slides, and user acceptance sign-off.
