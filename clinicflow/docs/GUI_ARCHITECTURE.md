# ClinicFlow — GUI Architecture & Design Document

## 1. Architectural Overview

The **ClinicFlow** desktop interface is engineered in pure Java Swing (Java 17 LTS) using a clean **Model-View-Service-Repository** architectural pattern.
The design prioritizes responsive desktop performance, non-blocking asynchronous execution, modular UI components, and strict decoupling between presentation components and business logic.

```
+---------------------------------------------------------------------------------------+
|                                    ClinicFlowApp                                      |
|  [Sidebar Navigation]                       [CardLayout Center Display]               |
|  • Dashboard           ----------------->   • DashboardPanel                          |
|  • Patients                                 • PatientManagementPanel                  |
|  • Doctors                                  • DoctorManagementPanel                   |
|  • Appointments                             • AppointmentManagementPanel              |
|  • Queue Management                         • QueueManagementPanel                    |
|  • Reminder Mgmt                            • ReminderManagementPanel                 |
|  • ML Model Insights                        • ModelInsightsPanel                      |
|  • About / Help                             • AboutPanel                              |
|  [Background SwingWorker]                   [Modal Dialogs]                           |
|  • Weka Model Loader                        • AppointmentBookingDialog                |
+---------------------------------------------------------------------------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
|                                  Service Layer                                        |
|  • PatientService       • DoctorService       • AppointmentService                    |
|  • ReminderService      • ModelInsightsService • WekaNoShowPredictionService          |
+---------------------------------------------------------------------------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
|                                Repository Layer (CSV)                                 |
|  • CsvPatientRepository       (data/app/patients.csv)                                 |
|  • CsvDoctorRepository        (data/app/doctors.csv)                                  |
|  • CsvAppointmentRepository   (data/app/appointments.csv)                             |
|  • CsvReminderRepository      (data/app/reminders.csv)                                |
+---------------------------------------------------------------------------------------+
```

---

## 2. Key UI Components & Screens

| Component Class | Role & Responsibilities |
| :--- | :--- |
| [`ClinicFlowApp`](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/ui/ClinicFlowApp.java) | Master `JFrame` managing sidebar navigation, CardLayout container, status bar, and background model loader. |
| [`DashboardPanel`](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/ui/DashboardPanel.java) | Executive dashboard showing 7 operational KPI cards, today's schedule, and custom attendance throughput chart. |
| [`PatientManagementPanel`](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/ui/PatientManagementPanel.java) | Patient directory with instant name/phone search, profile editor, deletion guard, and appointment history viewer. |
| [`DoctorManagementPanel`](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/ui/DoctorManagementPanel.java) | Physician roster, specialty assignments, consultation rooms, and duty availability toggling. |
| [`AppointmentBookingDialog`](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/ui/AppointmentBookingDialog.java) | Modal scheduling wizard with real-time Weka ML risk calculation, validation, and automated queue token issuance. |
| [`AppointmentManagementPanel`](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/ui/AppointmentManagementPanel.java) | Master schedule table with filter tabs (All, Today, Upcoming, High-Risk, Completed, Cancelled, Missed) and lifecycle actions. |
| [`QueueManagementPanel`](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/ui/QueueManagementPanel.java) | Live smart queue manager supporting patient check-in, consultation progression, and completion. |
| [`ReminderManagementPanel`](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/ui/ReminderManagementPanel.java) | Outpatient communication manager supporting simulated SMS and phone confirmation dispatch with persistent audit logs. |
| [`ModelInsightsPanel`](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/ui/ModelInsightsPanel.java) | Educational dashboard displaying Weka RandomForest architecture, feature schema, comparative evaluation table, and fairness notices. |
| [`AboutPanel`](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/ui/AboutPanel.java) | System architecture overview, technology stack, and academic coursework compliance documentation. |

---

## 3. Styling & Custom Visual Components

* **Design Palette ([`UITheme`](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/ui/common/UITheme.java))**:
  * Dark Navy (`#1B365D`) and Deep Teal (`#007791`) navigation accents.
  * Light Slate canvas background (`#F8FAFC`) with crisp white card containers (`#FFFFFF`).
  * Risk indicators: High (`#DC2626` Red), Medium (`#D97706` Amber), Low (`#16A34A` Green).
  * Consistent typography using `Segoe UI` font hierarchies (Title: 22pt bold, Header: 15pt bold, Body: 13pt plain).
* **Card Container ([`CardPanel`](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/ui/common/CardPanel.java))**: Reusable rounded panel component rendering smooth anti-aliased borders and backgrounds.
* **Attendance Chart ([`AttendanceChartComponent`](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/ui/common/AttendanceChartComponent.java))**: Pure Java 2D Graphics component rendering segmented proportional bars displaying attended, missed, in-queue, and scheduled appointments. Zero external JavaScript or charting dependencies.

---

## 4. Concurrency & Performance Optimization

### Asynchronous Model Loading via `SwingWorker`
The serialized Weka Random Forest model is approximately 234 MB. Deserializing 100 deep decision trees requires approximately 4.25 seconds.
To prevent freezing the Swing Event Dispatch Thread (EDT):
1. `ClinicFlowApp` constructs its UI immediately and renders the dashboard.
2. The status bar displays `"ML MODEL: LOADING IN BACKGROUND..."`.
3. A background `SwingWorker` loads and warms up the model in a worker thread.
4. Once completed, `done()` updates the status bar label to `"● ML MODEL: AVAILABLE (Weka Balanced RandomForest 100 Trees)"`.
5. If appointment booking is attempted before loading finishes, the system reports `"ML model unavailable"` and allows standard booking without blocking.

### Sub-Millisecond Live Inference
Once loaded into JVM memory, single-appointment feature extraction and Weka probability distribution evaluation (`distributionForInstance()`) executes in **under 0.5 milliseconds**, providing instantaneous risk previews as users type into the booking form.
