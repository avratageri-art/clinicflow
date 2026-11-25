# ClinicFlow — Context-Aware Clinical Appointment Triage & Queue Assistant

[![Java](https://img.shields.io/badge/Java-17%20LTS-orange.svg)](https://openjdk.org/)
[![ML Engine](https://img.shields.io/badge/ML-Weka%203.8.6%20RandomForest-green.svg)](https://www.cs.waikato.ac.nz/ml/weka/)
[![Live Demo](https://img.shields.io/badge/Live%20Demo-Render-46E3B7.svg)](https://clinicflow-9uhp.onrender.com/)
[![Tests](https://img.shields.io/badge/Tests-66%20JUnit%205%20Passing-brightgreen.svg)](https://junit.org/junit5/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **Live Deployed Demo:** [https://clinicflow-9uhp.onrender.com/](https://clinicflow-9uhp.onrender.com/)  
> **Health Endpoint:** [https://clinicflow-9uhp.onrender.com/health](https://clinicflow-9uhp.onrender.com/health)

---

## 1. Chosen Vertical & Problem Statement

- **Chosen Vertical:** **Open Innovation** — Context-Aware Clinical Appointment Triage & Queue Assistant.
- **Target Persona:** Outpatient Clinic Front-Desk Coordinator.

**Problem Statement:**  
Front-desk clinic coordinators lack real-time visibility into which scheduled appointments carry elevated no-show probability, which physicians are heavily booked, and which arrivals are waiting in the reception queue. Without booking-time risk scoring and prioritized outreach, high-risk bookings go unreminded and same-day clinic schedules suffer reactive bottlenecks.

---

## 2. Proposed Solution (100% Implemented in Code)

**ClinicFlow** pairs an embedded **Weka 3.8.6 Random Forest** no-show classifier (`WekaNoShowPredictionService`) with a deterministic **Context-Aware Clinical Assistant** (`ClinicalAssistantService`) and an accessible single-page coordinator console (`index.html`) to:

1. **Score No-Show Probability at Booking & Simulation Time (`POST /api/predict`, `POST /api/appointments`):**
   - Extracts 6 leak-free booking-time features (`leadTimeDays`, `appointmentWeekday`, `bookingWeekday`, `bookingHour`, `sameDayBooking`, `age`) and outputs a continuous probability `[0.0–1.0]`.
   - Applies deterministic risk stratification (`RiskCategory`):
     - `LOW` (`< 0.20`) -> `ReminderAction.STANDARD` (Standard automated SMS 24 hours prior)
     - `MEDIUM` (`0.20 – 0.499`) -> `ReminderAction.ENHANCED_SMS` (Enhanced SMS with direct confirmation link 48h and 24h prior)
     - `HIGH` (`>= 0.50`) -> `ReminderAction.PHONE_CALL` (Manual phone call by clinic front desk and priority confirmation)
   - Scans the next 10 clinic days (excluding Sundays) via `AppointmentService.findLowerRiskSlots` to return `recommendedAlternativeSlots`.

2. **Synthesize Live Clinic Signals Across 7 Copilot Intents (`POST /api/assistant/query`):**
   - Evaluates live operational state on every query and returns `contextSignals` (`totalAppointments`, `highRiskCount`, `unremindedHighRiskCount`, `activeQueueDepth`, `busiestDoctor`, `busiestDoctorLoad`), `reasoningSteps`, and executable `oneClickAction` payloads (`SEND_REMINDER`, `CHECK_IN`, `APPLY_SLOT`).
   - Supported intents in `ClinicalAssistantService`:
     - `CLINIC_BRIEFING` — Auto-loads on UI startup; surfaces unreminded `HIGH`/`MEDIUM`-risk bookings and `SCHEDULED` arrivals ready for check-in.
     - `HIGH_RISK_TRIAGE` — Ranks active appointments by `noShowProbability` descending with 1-click reminder/check-in actions.
     - `DOCTOR_LOAD_BALANCING` — Aggregates active appointment counts per registered physician.
     - `REMINDER_ACTION_PLAN` — Orders pending reminders by risk urgency (`HIGH` -> `MEDIUM` -> `LOW`).
     - `OPTIMAL_SLOT_FINDER` — Scans 10 upcoming clinic days for lowest-risk booking dates.
     - `PATIENT_CONTEXT` — Looks up a specific patient by ID or name, summarizes attendance/no-show history, and suggests their best upcoming slot.
     - `DOCTOR_CONTEXT` — Looks up a specific physician by ID, name, or specialty token and reports active load and availability.

3. **Execute 1-Click Outreach & Strict FIFO Queue Transitions (`POST /api/reminders/send`, `POST /api/queue/*`):**
   - Dispatches simulated local reminders (`ReminderService.simulateSendReminder`) and manages reception check-in (`CHECKED_IN`), consultation start (`IN_CONSULTATION`), completion (`ATTENDED`), no-show (`NO_SHOW`), and cancellation (`CANCELLED`).
   - **Advisory-Only Fairness:** ML risk scores never auto-cancel appointments or demote patients in today's FIFO queue (`AppointmentService.getTodayQueue`).

---

## 3. Feature-to-Code Traceability Matrix

| Capability | Backend Class | API Endpoint | Web UI Panel (`index.html`) |
| :--- | :--- | :--- | :--- |
| **Smart Clinical Copilot** | `ClinicalAssistantService`, `ClinicApiHandlers` | `POST /api/assistant/query` | Overview & Risk Simulator -> **Smart Clinical Assistant (AI Copilot)** (auto-briefing, `contextSignals` badges, recent turns, 1-click actions) |
| **Live Risk & Slot Simulator** | `WekaNoShowPredictionService`, `AppointmentService`, `ApiResponseMapper` | `POST /api/predict` | Overview & Risk Simulator -> **Live No-Show Risk & Slot Simulator** (clickable alternative slot buttons) |
| **Dashboard KPIs** | `ClinicFlowWebServer`, `ClinicApiHandlers` | `GET /api/dashboard` | Overview & Risk Simulator -> 5 live KPI cards |
| **Appointments & ML Booking** | `AppointmentService`, `ClinicApiHandlers` | `GET / POST /api/appointments` | Appointments -> **Book New Appointment** & **Scheduled Appointments** (**Check In** / **Cancel**) |
| **FIFO Clinic Queue** | `AppointmentService`, `ClinicApiHandlers` | `GET /api/queue`, `POST /api/queue/{checkin,start,complete,noshow,cancel}` | Clinic Queue (FIFO) -> **Today's Clinic Queue** |
| **Patient & Doctor Directory** | `PatientService`, `DoctorService`, `ClinicApiHandlers` | `GET / POST /api/patients`, `GET / POST /api/doctors` | Patients & Reminders -> **Register Patient** & **Register Doctor** |
| **Reminder Outreach Audit** | `ReminderService`, `ClinicApiHandlers` | `GET /api/reminders`, `POST /api/reminders/send` | Patients & Reminders -> **Reminder Outreach Queue** |
| **ML Insights & Retraining** | `ModelInsightsService`, `NoShowModelTrainer` | `GET /api/model/insights`, `POST /api/model/retrain` | ML Model Insights -> **ML Engine & Evaluation Metrics** (**Retrain Model**) |
| **Security & Sanitization** | `WebSecuritySupport`, `CsvSanitizer` | All `/api/*` & `/` | `415` JSON `Content-Type` check, `413` `64 KB` limit, `429` rate limit (`120 req/min`, `2048` IP cap), XSS/CSV guards, DOM `escapeHtml` |

---

## 4. Security, Efficiency & Accessibility (WCAG 2.1 AA/AAA)

- **Security:** `WebSecuritySupport` enforces `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Permissions-Policy: camera=(), microphone=(), geolocation=()`, `Cross-Origin-Opener-Policy: same-origin`, `Cross-Origin-Resource-Policy: same-origin`, `Cache-Control: no-store, no-cache, must-revalidate` + `Pragma: no-cache` on `/api/*`, and a `Content-Security-Policy` (`frame-ancestors 'none'`) on HTML. `CsvSanitizer` neutralizes CSV formula triggers (`=`, `+`, `-`, `@`, `\t`, `\r`). Docker runs as non-root `USER clinicflow`.
- **Efficiency:** Weka `RandomForest` is loaded once in memory; `index.html` bytes are preloaded once into `cachedIndexHtml`; `HttpServer` uses a bounded `Executors.newFixedThreadPool(8)`; `ClinicalAssistantService` uses `O(1)` `Map<Long, Patient>` and `Map<Long, Doctor>` indexes; `Dockerfile` runs with `-XX:+UseSerialGC -Xms64m -Xmx300m`.
- **Accessibility:** `<html lang="en">`, skip link (`#main-content`), `<header>`, `<nav aria-label="Primary navigation">`, `<main id="main-content">`, `<footer>`, `<caption class="sr-only">` and `<th scope="col">` on all tables, `<label for="...">` + `aria-describedby` + `aria-required="true"` on form fields, `role="status"` / `role="alert"` live regions, `@media (prefers-reduced-motion: reduce)`, `Alt+1`..`Alt+5` tab shortcuts, and a header **High Contrast** toggle (`#contrast-toggle` with `aria-pressed`).

---

## 5. Scope & Operational Constraints

- **Advisory-Only Predictions:** Risk tiers and reminder actions never cancel appointments or reorder the FIFO check-in queue.
- **Self-Contained Seed Dataset:** `clinicflow/data/raw/sample_appointments.csv` (200 records) is bundled for fast container startup and testing. If `clinicflow/data/raw/appointments.csv` is supplied locally, `ApplicationConfig.getEffectiveRawDataPath()` uses it automatically.
- **Local CSV Persistence:** Operational records (`patients.csv`, `doctors.csv`, `appointments.csv`, `reminders.csv`) are persisted via atomic CSV writes in `data/app/` (resetting on stateless container recycle).
- **Simulated Reminders:** `ReminderService.simulateSendReminder` logs reminder dispatches locally in `reminders.csv` without external messaging calls.

---

## 6. Live Demo Walkthrough & Local Commands

1. Open [https://clinicflow-9uhp.onrender.com/](https://clinicflow-9uhp.onrender.com/) — the **Smart Clinical Assistant** auto-loads `CLINIC_BRIEFING` with live `contextSignals` badges and 1-click actions.
2. Query the assistant directly:
```bash
curl -s -X POST https://clinicflow-9uhp.onrender.com/api/assistant/query \
  -H "Content-Type: application/json" \
  -d '{"query":"Who are my highest-risk patients?"}'
```
3. Run a live prediction simulation:
```bash
curl -s -X POST https://clinicflow-9uhp.onrender.com/api/predict \
  -H "Content-Type: application/json" \
  -d '{"age":30,"gender":"F","appointmentDay":"2026-12-05","neighbourhood":"CENTRO"}'
```
4. Build and test locally:
```bash
# Run all 66 JUnit 5 tests
mvn -f clinicflow/pom.xml clean test

# Package and run web server on http://localhost:8080
mvn -f clinicflow/pom.xml package -DskipTests
java -Djava.awt.headless=true -jar clinicflow/target/clinicflow-1.0.0-SNAPSHOT.jar --server

# Build & run Docker container
docker build -t clinicflow:latest .
docker run --rm -p 8080:8080 -e PORT=8080 clinicflow:latest
```
