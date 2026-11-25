# ClinicFlow — Smart Patient No-Show Prediction & Clinic Queue Assistant

[![Java](https://img.shields.io/badge/Java-17%20LTS-orange.svg)](https://openjdk.org/)
[![ML Engine](https://img.shields.io/badge/ML-Weka%203.8.6%20RandomForest-green.svg)](https://www.cs.waikato.ac.nz/ml/weka/)
[![Cloud Run](https://img.shields.io/badge/Deploy-Google%20Cloud%20Run-4285F4.svg)](https://cloud.google.com/run)
[![Tests](https://img.shields.io/badge/Tests-JUnit%205%20%2B%20AssertJ-brightgreen.svg)](https://junit.org/junit5/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**ClinicFlow** is a Cloud Run / Render–ready Java 17 web service, ML decision-support engine, and clinical queue assistant for outpatient clinics. It predicts patient appointment no-show risk at booking time using a **Weka Random Forest** classifier and pairs predictions with context-aware outreach actions, lower-risk slot recommendations, and a strict FIFO clinic queue.

> **Live Deployed Demo:** [https://clinicflow-9uhp.onrender.com/](https://clinicflow-9uhp.onrender.com/)

---

## 1. Chosen Vertical

**Open Innovation** — Healthcare Operations & Smart Clinical Triage Assistant.

---

## 2. Problem Statement & Persona

**Persona:** Outpatient Clinic Front-Desk Coordinator & Care Navigator.

Missed outpatient appointments ("no-shows") leave physician consultation slots idle, lengthen patient backlogs, and disrupt same-day clinic flow. In real clinical datasets, ~80% of appointments are attended, which causes naive majority-class heuristics to miss high-risk bookings until it is too late to intervene.

ClinicFlow acts as an intelligent, non-punitive assistant for clinic coordinators:
- Evaluates every new booking or simulated slot in real time.
- Recommends targeted outreach tailored to the patient's risk tier.
- Surfaces lower-risk alternative appointment dates when a booking has elevated no-show risk.
- Manages same-day check-ins and consultations through a fair, FIFO queue.

---

## 3. Approach & Decision Logic

### ML Prediction Pipeline
ClinicFlow trains and evaluates three Weka 3.8.6 classifiers on a chronological 80/20 split (preventing temporal leakage):
1. **`ZeroR`** (majority-class baseline)
2. **`RandomForest`** (100 trees, seed `42`)
3. **Class-Balanced `RandomForest`** (`FilteredClassifier` with `ClassBalancer` applied strictly to the training partition)

The pipeline selects the optimal model by minority-class (`YES` no-show) F1-score while preserving ROC-AUC, and serializes both the classifier and its `ModelMetadata` to `models/`. On startup in a fresh container (when no `.model` file is present), ClinicFlow automatically trains on the compact self-contained dataset (`clinicflow/data/raw/sample_appointments.csv`) so it runs out-of-the-box in seconds.

### Feature Engineering (Zero Target Leakage)
Only features known at the exact moment of booking are used:
- `leadTimeDays` — days between `ScheduledDay` and `AppointmentDay`
- `appointmentWeekday` — day of week of the consultation
- `bookingWeekday` — day of week the booking was created
- `bookingHour` — hour of day the booking was created
- `sameDayBooking` — `YES` / `NO` indicator for walk-in/same-day bookings
- `age` — validated patient age (`0–125`)

Identifiers (`PatientId`, `AppointmentID`) and post-booking outcomes (`SMS_received`) are strictly excluded.

### Risk Stratification & Context-Aware Actions
Configurable via `CLINICFLOW_LOW_RISK_THRESHOLD` (default `0.20`) and `CLINICFLOW_HIGH_RISK_THRESHOLD` (default `0.50`):

| Risk Tier | No-Show Probability | Recommended Outreach (`ReminderAction`) | Smart Queue / Scheduling Guidance |
| :--- | :--- | :--- | :--- |
| **LOW** | `< 20%` | `STANDARD` — Automated SMS 24h prior | Normal slot confirmation |
| **MEDIUM** | `20% – 49.9%` | `CONFIRMATION_LINK` — Two-way SMS confirmation link | Suggest lower-risk alternative dates if needed |
| **HIGH** | `>= 50%` | `PHONE_CALL` — Coordinator phone outreach | Surface top 3 lower-risk alternative slots + backfill planning |

---

## 4. System Architecture & How It Works

```text
Accessible Web UI (index.html: <header>, <nav>, <main>, ARIA)
        │  REST / JSON (0.0.0.0:$PORT, default 8080)
        ▼
ClinicFlowWebServer (com.sun.net.httpserver.HttpServer)
        │
        ├── /health, /api/dashboard, /api/predict
        ├── /api/patients, /api/doctors, /api/appointments
        ├── /api/queue/* (checkin, start, complete, noshow, cancel)
        └── /api/reminders/*, /api/model/insights, /api/model/retrain
        │
        ▼
Service Layer (AppointmentService, PatientService, DoctorService,
               ReminderService, WekaNoShowPredictionService, ModelInsightsService)
        │
        ├──────────────────────────────────────┐
        ▼                                      ▼
Csv*Repository (atomic temp-write + rename)   Weka ML Engine (NoShowModelTrainer,
data/app/*.csv                                 DataPreprocessor, FeatureExtractor,
                                               NoShowPredictor, ModelEvaluator)
```

### API Endpoints
| Method & Path | Purpose |
| :--- | :--- |
| `GET /health` | Service health and ML model readiness check |
| `GET /api/dashboard` | Real-time KPI summary, queue count, and risk distribution |
| `POST /api/predict` | Live Weka no-show prediction, `triageGuidance`, and lower-risk slots |
| `GET / POST /api/patients` | List or register patients (validated + CSV-sanitized) |
| `GET / POST /api/doctors` | List or register attending doctors |
| `GET / POST /api/appointments` | List or book appointments with ML risk & queue token assignment |
| `GET /api/queue` | Retrieve today's active FIFO queue |
| `POST /api/queue/checkin` | Check in a scheduled appointment into today's queue |
| `POST /api/queue/start` | Start consultation (`IN_CONSULTATION`) |
| `POST /api/queue/complete` | Complete consultation (`ATTENDED`) |
| `POST /api/queue/noshow` | Record patient no-show (`NO_SHOW`) |
| `POST /api/queue/cancel` | Cancel an appointment (`CANCELLED`) |
| `GET /api/reminders` | List pending/sent reminder outreach records |
| `POST /api/reminders/send` | Simulate reminder dispatch and update audit log |
| `GET /api/model/insights` | Model metadata, feature schema, and evaluation metrics |
| `POST /api/model/retrain` | Retrain Weka Random Forest pipeline on demand |

### Directory Structure
```text
.
├── Dockerfile                  # Multi-stage Java 17 build (-XX:+UseSerialGC -Xms64m -Xmx300m)
├── render.yaml                 # Render.com Blueprint (free-tier Docker web service)
├── .dockerignore               # Excludes build outputs, .git, and local staging dirs
├── .gitignore                  # Excludes target/, large datasets/models, IDE metadata
├── .env.example                # Optional environment variable overrides
├── README.md                   # Hackathon technical documentation
├── docs/                       # Architecture diagrams
└── clinicflow/
    ├── pom.xml                 # Maven build (Java 17, Weka 3.8.6, Commons CSV, JUnit 5)
    ├── data/
    │   ├── raw/sample_appointments.csv   # Compact self-contained dataset (< 20 KB)
    │   └── app/                          # Seeded operational CSV stores
    └── src/
        ├── main/java/com/clinicflow/
        │   ├── Main.java                 # Web server (--server / $PORT), CLI (--cli), Swing GUI
        │   ├── ml/                       # Preprocessing, FeatureExtractor, Trainer, Evaluator
        │   ├── model/                    # Patient, Doctor, Appointment, PredictionResult, RiskCategory
        │   ├── repository/               # CSV & InMemory repositories + CsvSanitizer
        │   ├── service/                  # Clinical business logic & ML prediction service
        │   ├── ui/                       # Desktop Swing panels (optional local GUI mode)
        │   ├── web/                      # ClinicFlowWebServer & JsonUtil
        │   └── util/                     # ApplicationConfig & domain exceptions
        ├── main/resources/web/index.html # Accessible single-page web UI
        └── test/java/com/clinicflow/     # JUnit 5 + AssertJ unit & web integration tests
```

### Security & Accessibility Safeguards
- **No hardcoded secrets:** All configuration is environment-driven (`.env.example`); no API keys, tokens, or credentials exist in source control.
- **Input validation & CSV sanitization:** Request bodies are bounded (`64 KB`), string lengths and age bounds (`0–120`) are enforced, CSV cells are protected against formula injection (`CsvSanitizer`), and errors return structured JSON without stack traces.
- **Container & HTTP hardening:** Multi-stage Docker build runs under non-root user `clinicflow` (`-Xmx300m` for 512 MB containers) and sets `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, and `Content-Security-Policy`.
- **Accessibility (WCAG 2.1 AA):** Semantic `<header>`, `<nav>`, `<main>`, and `<footer>` landmarks, skip-to-content link, explicit `<label for="...">` bindings, `aria-label` on controls, `aria-live="polite"` status regions, `:focus-visible` keyboard rings, text+icon risk badges (`✓ LOW`, `! MEDIUM`, `▲ HIGH`), and a header **High-Contrast Mode** toggle (`aria-pressed`).

---

## 5. Assumptions & Limitations

- **Advisory-Only AI:** Risk predictions guide outreach intensity and slot suggestions only. High-risk patients are **never** denied care or demoted in the clinic queue (same-day queue order is strictly FIFO by check-in time).
- **Compact Default Dataset:** To keep the repository lightweight (< 3 MB), `sample_appointments.csv` (200 records) is bundled for instant startup and testing. If the full 110,527-row Kaggle Medical Appointment No-Shows CSV is placed at `clinicflow/data/raw/appointments.csv`, ClinicFlow automatically uses it.
- **Local & Ephemeral Persistence:** Operational records (`patients.csv`, `doctors.csv`, `appointments.csv`, `reminders.csv`) are persisted locally via atomic CSV writes. On stateless free-tier containers (Render / Cloud Run), container restarts reset ephemeral filesystem state unless backed by a persistent disk or external database.
- **Simulated Reminders:** SMS/phone dispatches are simulated and audited locally without external messaging gateway charges.

---

## 6. Quickstart & Google Cloud Run Deployment

### Run Automated Tests
```bash
mvn -f clinicflow/pom.xml test
```

### Run Locally (Web Server on `http://localhost:8080`)
```bash
mvn -f clinicflow/pom.xml package -DskipTests
java -Djava.awt.headless=true -jar clinicflow/target/clinicflow-1.0.0-SNAPSHOT.jar --server
```

### Run CLI Verification Mode
```bash
java -Djava.awt.headless=true -jar clinicflow/target/clinicflow-1.0.0-SNAPSHOT.jar --cli
```

### Build & Run Docker Container Locally
```bash
docker build -t clinicflow:latest .
docker run --rm -p 8080:8080 -e PORT=8080 clinicflow:latest
```

### Deploy to Google Cloud Run
```bash
gcloud run deploy clinicflow \
  --source . \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --port 8080
```
