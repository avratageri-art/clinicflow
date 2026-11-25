# ClinicFlow — Desktop Application User Guide

Welcome to **ClinicFlow: Smart Clinic Appointment and Queue Management System**.
This user guide provides step-by-step instructions for running, operating, and verifying the desktop application.

---

## 1. How to Launch the Application

### Option A: Using the Windows Batch Launcher (Recommended)
Double-click [`run-clinicflow.bat`](file:///D:/JAVA_ML/clinicflow/run-clinicflow.bat) in the project root:
```bat
run-clinicflow.bat
```
The script automatically verifies your Java 17 runtime, locates the packaged JAR or builds via Maven, and launches the graphical desktop interface.

### Option B: Using Apache Maven
From a terminal in `D:\JAVA_ML\clinicflow`:
```powershell
$env:JAVA_HOME = "D:\JAVA_ML\tools\jdk-17.0.12+7"
$env:Path = "D:\JAVA_ML\tools\jdk-17.0.12+7\bin;D:\JAVA_ML\tools\apache-maven-3.9.6\bin;$env:Path"
mvn exec:java
```

### Option C: Using Packaged Standalone JAR
```powershell
java -cp "target\clinicflow-1.0.0-SNAPSHOT.jar;target\dependency\*" com.clinicflow.Main
```

### Option D: Running Headless CLI Mode (Verification / CI)
```powershell
mvn exec:java -Dexec.args="--cli"
```

---

## 2. Navigating the Interface

The application features a dark navy/teal sidebar with 8 major modules:
1. **Dashboard:** High-level operational metrics, live queue count, today's schedule, and attendance breakdown chart.
2. **Patients:** Patient registration, contact updating, medical history flags, search, deletion, and appointment history.
3. **Doctors:** Attending physician roster, medical specialty assignments, consultation rooms, and duty availability toggling.
4. **Appointments:** Master schedule table, filter tabs (All, Today, Upcoming, High-Risk, Completed, Cancelled, Missed), and state transitions.
5. **Queue Management:** Real-time today's waiting room queue, patient check-in, token progression, and consultation completion.
6. **Reminder Management:** Automated sync of upcoming appointments requiring patient outreach, local SMS/phone simulation, and audit history.
7. **ML Model Insights:** Deep-dive architectural report on Weka RandomForest, training metadata, feature schema, comparative evaluation against ZeroR, confusion matrix, student guide, and demographic limitation warnings.
8. **About / Help:** Architecture overview and academic assignment compliance matrix.

---

## 3. Core Operational Workflows

### 3.1 Registering a New Patient
1. Click **Patients** in the left sidebar.
2. Click the **+ Register Patient** button in the upper-right corner.
3. Fill in the patient details:
   - Full Name (required)
   - Gender (Male / Female)
   - Age (0–125)
   - Contact Phone
   - Medical Conditions (Hypertension, Diabetes, Alcoholism, Welfare enrollment)
4. Click **Save Patient**. The record is saved and immediately written to [`data/app/patients.csv`](file:///D:/JAVA_ML/clinicflow/data/app/patients.csv).

### 3.2 Adding or Managing Doctors
1. Click **Doctors** in the left sidebar.
2. Click **+ Add Doctor** to register a new physician (Name, Specialty, Room, Phone).
3. To mark a physician on-leave or unavailable, select their row and click **Toggle Availability**. Unavailable doctors cannot receive new appointment bookings.
4. To remove a doctor, click **Remove Doctor** (the system prevents deletion if future scheduled appointments exist).

### 3.3 Booking an Appointment with Live ML Risk Assessment
1. Click **+ Book Appointment** (available from Dashboard, Patients, or Appointments screen).
2. Select the registered **Patient** from the dropdown.
3. Select an available **Attending Doctor**.
4. Enter the consultation **Date** (`YYYY-MM-DD`) and select a **Time Slot**.
5. As soon as Patient and Date are entered, the **Real-Time Weka Machine Learning Risk Assessment** card calculates:
   - **Predicted No-Show Score:** e.g., `62.7%`
   - **Attendance Probability:** e.g., `37.3%`
   - **Risk Level Badge:** `LOW` (Green), `MEDIUM` (Amber), `HIGH` (Red)
   - **Recommended Action:** `STANDARD_CONFIRMATION`, `ENHANCED_SMS`, or `PHONE_CALL`
   - *Label Notice:* "Model-estimated no-show score – not a calibrated probability."
6. Click **Confirm Booking & Issue Token**. The system generates a queue token (e.g. `Q-002`) and writes the record to [`data/app/appointments.csv`](file:///D:/JAVA_ML/clinicflow/data/app/appointments.csv).

### 3.4 Managing the Smart Waiting Queue
1. Click **Queue Management** in the sidebar.
2. When a patient arrives at the clinic, click **+ Check In Patient** and choose their appointment.
3. The patient is assigned their queue token and placed into the waiting room table.
4. **Fairness Guarantee:** Queue sequencing is strictly FIFO based on arrival time. The ML risk score is advisory for staff reminders and **never** lowers a patient's position in the queue.
5. When the doctor is ready, select the patient and click **Call Next / Start Consultation** (status becomes `IN_CONSULTATION`).
6. When consultation finishes, click **Finish Consultation** (status becomes `ATTENDED`).

### 3.5 Simulating Patient Reminders
1. Click **Reminder Management** in the sidebar.
2. Click **Sync with Appointments** to automatically detect all upcoming consultations.
3. Appointments flagged as `HIGH` risk automatically recommend front-desk telephone outreach (`PHONE_CALL`).
4. Select a pending reminder and click **Simulate Send Selected Reminder**.
5. The system records the dispatched reminder with a timestamp and saves the audit log to [`data/app/reminders.csv`](file:///D:/JAVA_ML/clinicflow/data/app/reminders.csv).
6. View past reminders in the **Sent Reminder Audit History** tab.

---

## 4. Persistent Storage Locations

All user-created data is stored persistently under the `data/app/` directory:
- [`data/app/patients.csv`](file:///D:/JAVA_ML/clinicflow/data/app/patients.csv): Registered patients.
- [`data/app/doctors.csv`](file:///D:/JAVA_ML/clinicflow/data/app/doctors.csv): Registered physicians and availability states.
- [`data/app/appointments.csv`](file:///D:/JAVA_ML/clinicflow/data/app/appointments.csv): Master appointments, tokens, ML prediction scores, and lifecycle states.
- [`data/app/reminders.csv`](file:///D:/JAVA_ML/clinicflow/data/app/reminders.csv): Simulated reminder dispatch audit trail.

Historical training records (`data/raw/appointments.csv` and `data/processed/clean_appointments.csv`) are strictly preserved and never modified by application activity.
