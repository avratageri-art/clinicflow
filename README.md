# ClinicFlow — Smart Patient No-Show Prediction & Clinical Queue Copilot

[![Java](https://img.shields.io/badge/Java-17%20LTS-orange.svg)](https://openjdk.org/)
[![ML Engine](https://img.shields.io/badge/ML-Weka%203.8.6%20RandomForest-green.svg)](https://www.cs.waikato.ac.nz/ml/weka/)
[![Live Demo](https://img.shields.io/badge/Live%20Demo-Render-46E3B7.svg)](https://clinicflow-9uhp.onrender.com/)
[![Tests](https://img.shields.io/badge/Tests-49%2B%20JUnit%205%20Passing-brightgreen.svg)](https://junit.org/junit5/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **Live Deployed Demo:** [https://clinicflow-9uhp.onrender.com/](https://clinicflow-9uhp.onrender.com/)  
> **Health Endpoint:** [https://clinicflow-9uhp.onrender.com/health](https://clinicflow-9uhp.onrender.com/health)

### Executive Summary: Problem -> AI Copilot Solution -> Measurable Clinical Impact
- **Problem:** Outpatient clinics lose ~20% of scheduled slots to patient no-shows, leaving physicians idle while high-need patients wait weeks.
- **AI Copilot Solution:** ClinicFlow combines a **Weka Random Forest** classifier with a **context-aware Smart Clinical Assistant** (`POST /api/assistant/query`) that inspects live patients, doctors, queue load, and pending reminders to recommend targeted outreach and lower-risk booking slots.
- **Measurable Clinical Impact:** Identifies elevated-risk bookings at scheduling time (`LOW < 20%`, `MEDIUM 20–49.9%`, `HIGH >= 50%`), prioritizes coordinator phone/SMS outreach, balances physician load, and preserves **strict FIFO queue fairness** with zero automated patient denial.

---

## 1. Problem Statement & Chosen Vertical

**Chosen Vertical:** **Open Innovation** — Healthcare Operations & Smart Clinical Triage Assistant.  
**Target Persona:** Outpatient Clinic Front-Desk Coordinator & Care Navigator.

Missed outpatient appointments ("no-shows") account for ~20% of scheduled clinic slots in real-world public health systems, leaving physicians idle while other patients wait weeks for care. Because ~80% of patients do attend, naive rules fail to flag elevated-risk bookings early enough for non-punitive outreach, and coordinators lack a unified tool to balance doctor load, dispatch targeted reminders, and manage same-day walk-ins fairly.

---

## 2. Proposed AI Solution & Decision Logic

**ClinicFlow** pairs a **Weka 3.8.6 Random Forest** no-show prediction engine with a **Context-Aware Smart Clinical Assistant** (`ClinicalAssistantService`) and a **strict FIFO Queue Manager**:

1. **Booking-Time ML Prediction (Zero Target Leakage):**
   - Evaluates every booking or simulation using only features known at booking time: `leadTimeDays`, `appointmentWeekday`, `bookingWeekday`, `bookingHour`, `sameDayBooking`, and `age`.
   - Compares `ZeroR`, standard `RandomForest` (100 trees, seed `42`), and `ClassBalancer` + `RandomForest` on a chronological 80/20 split, automatically persisting and serving the best classifier.
2. **Context-Aware Smart Clinical Assistant (`POST /api/assistant/query`):**
   - Inspects live clinic state (patients, doctors, active bookings, ML probabilities, FIFO queue load, and pending reminders) to answer natural-language coordinator queries across 5 operational intents:
     - `HIGH_RISK_TRIAGE` — Ranks active patients by no-show risk, explains *why* each was flagged (lead time + ML score), and surfaces 1-click reminder actions.
     - `DOCTOR_LOAD_BALANCING` — Compares active slot load across physicians to spot bottlenecks.
     - `REMINDER_ACTION_PLAN` — Prioritizes pending outreach (`PHONE_CALL` -> `CONFIRMATION_LINK` -> `STANDARD`).
     - `OPTIMAL_SLOT_FINDER` — Scans the next 10 clinic days (excluding Sundays) to recommend the lowest-risk alternative dates with 1-click booking pre-fill.
     - `CLINIC_BRIEFING` — Auto-loads on startup with a live operational summary and next priority action.
3. **Risk Stratification & Advisory-Only Fairness:**
   - **LOW (`< 20%`):** `STANDARD` automated SMS 24h prior.
   - **MEDIUM (`20% – 49.9%`):** `CONFIRMATION_LINK` two-way SMS + alternative slot suggestions.
   - **HIGH (`>= 50%`):** `PHONE_CALL` coordinator outreach + top 3 lower-risk alternative slots.
   - **Non-Punitive Rule:** ML predictions never auto-cancel bookings or demote patients in today's FIFO queue.

---

## 3. Verified Feature-to-Code Traceability Matrix

Every feature documented here is implemented and wired end-to-end:

| Capability | Backend Class | API Endpoint | Web UI Panel (`index.html`) |
| :--- | :--- | :--- | :--- |
| **Smart Clinical Copilot** | `ClinicalAssistantService` | `POST /api/assistant/query` | Overview -> **Smart Clinical Assistant** (auto-briefing, prompt input, 4 quick chips, 1-click actions) |
| **Live Risk & Slot Simulator** | `WekaNoShowPredictionService`, `AppointmentService` | `POST /api/predict` | Overview -> **Live No-Show Risk & Slot Simulator** (clickable low-risk slot buttons) |
| **Dashboard KPIs** | `ClinicFlowWebServer` | `GET /api/dashboard` | Overview -> 5 live KPI cards |
| **Appointments & ML Booking** | `AppointmentService` | `GET / POST /api/appointments` | Appointments -> Book form, triage guidance, table with **Check In** / **Cancel** |
| **FIFO Clinic Queue** | `AppointmentService` | `GET /api/queue`, `POST /api/queue/{checkin,start,complete,noshow,cancel}` | Clinic Queue (FIFO) -> Check-In, Start, Complete, No-Show |
| **Patient & Doctor Directory** | `PatientService`, `DoctorService` | `GET / POST /api/patients`, `GET / POST /api/doctors` | Patients & Reminders -> Register Patient & Register Doctor forms |
| **Reminder Outreach Audit** | `ReminderService` | `GET /api/reminders`, `POST /api/reminders/send` | Patients & Reminders -> Reminder Outreach Queue |
| **ML Insights & Retraining** | `ModelInsightsService`, `NoShowModelTrainer` | `GET /api/model/insights`, `POST /api/model/retrain` | ML Model Insights -> Metrics, features, **Retrain Model** button |

---

## 4. Security, Efficiency & Accessibility (WCAG 2.1 AA)

- **Security:** `CsvSanitizer` prevents CSV formula injection (`=`, `+`, `-`, `@`, `\t`, `\r`); `POST` payloads are bounded to `64 KB`; string lengths and ages (`0–120`) are validated; HTTP responses set `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, and `Content-Security-Policy`; Docker runs as non-root user `clinicflow`.
- **Efficiency:** Weka `RandomForest` is loaded once into memory; `HttpServer` uses a bounded `Executors.newFixedThreadPool(8)`; `Dockerfile` tunes JVM memory (`-XX:+UseSerialGC -Xms64m -Xmx300m`) for 512 MB containers.
- **Accessibility:** `<html lang="en">`, semantic `<header>`, `<nav>`, `<main>`, `<footer>`, skip-to-content link, `<label for="...">` bindings, `aria-live="polite"`, `:focus-visible` outlines, text+icon risk badges (`✓ LOW`, `! MEDIUM`, `▲ HIGH`), and a header **High-Contrast Mode** toggle (`aria-pressed`).

---

## 5. Assumptions & Limitations

- **Advisory-Only AI:** Predictions guide coordinator outreach and slot suggestions only; no automated patient denial or queue demotion occurs.
- **Compact Default Dataset:** `clinicflow/data/raw/sample_appointments.csv` (200 records, `< 20 KB`) is bundled for instant startup and retraining. If the full 110,527-row Kaggle dataset is placed at `clinicflow/data/raw/appointments.csv`, ClinicFlow uses it automatically.
- **Ephemeral Container Persistence:** Operational CSVs (`data/app/*.csv`) are written atomically to local disk; on stateless free-tier containers (Render / Cloud Run), state resets on container restart unless backed by a persistent volume.
- **Simulated Reminders:** SMS/phone dispatches are simulated locally without external messaging gateway charges.

---

## 6. Live Demo Walkthrough

1. Open [https://clinicflow-9uhp.onrender.com/](https://clinicflow-9uhp.onrender.com/) — the **Smart Clinical Assistant** auto-loads a live operational briefing on startup.
2. Click any quick-action chip (or ask `"Assess patient 1001"` / `"Check Dr. Beatriz Silva"`):
```bash
curl -s -X POST https://clinicflow-9uhp.onrender.com/api/assistant/query \
  -H "Content-Type: application/json" \
  -d '{"query":"Who are my highest-risk patients?"}'
```
Sample response:
```json
{
  "intent": "HIGH_RISK_TRIAGE",
  "answer": "Found 1 active scored appointments (0 MEDIUM/HIGH risk)...",
  "reasoningSteps": ["Inspected 1 active/upcoming appointments using Weka Random Forest probabilities."],
  "recommendedActions": [{"patientName": "K Kalash", "riskCategory": "LOW", "oneClickAction": {"type": "SEND_REMINDER"}}]
}
```
3. Run a live risk simulation:
```bash
curl -s -X POST https://clinicflow-9uhp.onrender.com/api/predict \
  -H "Content-Type: application/json" \
  -d '{"age":30,"gender":"F","appointmentDay":"2026-12-05","neighbourhood":"CENTRO"}'
```
Returns `noShowProbability`, `riskCategory`, `triageGuidance`, and clickable `recommendedAlternativeSlots`.

---

## 7. Quickstart & Deployment

```bash
# Run full automated test suite (JUnit 5 + AssertJ)
mvn -f clinicflow/pom.xml test

# Build and run web server locally on http://localhost:8080
mvn -f clinicflow/pom.xml package -DskipTests
java -Djava.awt.headless=true -jar clinicflow/target/clinicflow-1.0.0-SNAPSHOT.jar --server

# Build & run Docker container
docker build -t clinicflow:latest .
docker run --rm -p 8080:8080 -e PORT=8080 clinicflow:latest
```

Deploy to **Render** via [render.yaml](render.yaml) or **Google Cloud Run**:
```bash
gcloud run deploy clinicflow --source . --platform managed --region asia-south1 --allow-unauthenticated --port 8080 --memory 1Gi
```
