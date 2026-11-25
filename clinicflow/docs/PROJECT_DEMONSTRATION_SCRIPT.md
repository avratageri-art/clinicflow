# ClinicFlow — 7–10 Minute Academic Demonstration Script

This script provides an exact, step-by-step presentation roadmap for students conducting a live viva or project evaluation. It specifies timings, spoken talking points, UI actions, and what to highlight on screen.

---

## Pre-Demonstration Checklist (Perform in Advance)
1. **Environment Setup:** Ensure Java 17 and Maven 3.8+ are installed.
2. **Pre-Loaded Model:** Verify that `models/no-show-random-forest.model` (233.9 MB) exists.
3. **Clean Initial Data:** Verify that `data/app/` contains the standard demo seed files (`patients.csv`, `doctors.csv`).
4. **Internet Access:** Not required. ClinicFlow operates 100% offline.
5. **Launch:** Run `run-clinicflow.bat` or execute `mvn exec:java` prior to speaking so the window is open and the model is fully loaded (`● ML Model Ready`).

---

## 10-Minute Live Demonstration Sequence

### [00:00 – 01:00] Step 1: Real-World Healthcare Problem
* **Action:** Stand by the open ClinicFlow application displaying the **Dashboard**.
* **Presenter Script:**
  > "Respected evaluators, outpatient medical appointment no-shows cause substantial operational disruptions across clinics worldwide. When patients miss appointments without advance cancellation, physician time and clinical examination rooms remain idle, while other sick patients experience prolonged waiting times. 
  > 
  > Traditional clinic systems treat every booking identically with generic, automated SMS. Our project, **ClinicFlow**, embeds a native Machine Learning engine inside a Java 17 desktop application to predict appointment no-show probability in real time at the moment of booking, allowing reception staff to prioritize proactive reminders while maintaining strict, non-punitive queue fairness."

---

### [01:00 – 02:00] Step 2: Genuine Dataset & Anomaly Pruning
* **Action:** Switch to the **About & Help** screen or refer to the report slides.
* **Presenter Script:**
  > "To ensure academic authenticity, we do not use synthetic data. ClinicFlow is trained on the genuine, public Medical Appointment No-Shows dataset comprising 110,527 historical records from the public healthcare network (SUS) of Vitória, Brazil. 
  > 
  > During data preprocessing in Java, our pipeline identified and pruned exactly 6 clerical anomalies (5 negative lead-time bookings and 1 negative age typo) without synthetic imputation, leaving 110,521 valid records. To eliminate future-to-past data leakage, we partitioned the data chronologically into an 88,417-record training set (earliest 80%) and an untouched 22,104-record test set (latest 20%)."

---

### [02:00 – 03:00] Step 3: Algorithm Selection & Architecture
* **Action:** Click **ML Model Insights** on the sidebar.
* **Presenter Script:**
  > "We selected a **Balanced Random Forest Classifier** with 100 decision trees via Weka 3.8.6. Random Forest creates an ensemble of decorrelated decision trees using bagging and random feature subspace sampling.
  > 
  > In outpatient scheduling, we face a 4:1 class imbalance (80% attend, 20% miss). A naive ZeroR baseline predicts 'Attended' for every visit, showing 88.09% nominal accuracy but catching zero no-shows. By using Weka's ClassBalancer filter, our Balanced Random Forest achieves 77.50% accuracy with a 32.93% recall—successfully detecting 867 missed appointments. In clinical operations, Recall is vastly superior to raw Accuracy."

---

### [03:00 – 04:00] Step 4: Executive Dashboard & Java2D Attendance Chart
* **Action:** Click **Dashboard** on the sidebar.
* **Presenter Script:**
  > "Here is our Executive Dashboard. The 4 KPI cards display live totals for Active Bookings, Today's Consultations, Checked-in Waiting Patients, and High-Risk Appointments. 
  > 
  > On the right is a custom-rendered Java2D horizontal bar chart illustrating the overall attendance distribution. Notice that the entire UI was built using Java Swing with Segoe UI typography and responsive card layouts without third-party web frameworks."

---

### [04:00 – 05:00] Step 5 & 6: Patient and Doctor Management
* **Action:** Click **Patient Directory**, then click **Register Patient**.
  * Enter: ID: `1005`, Name: `Fernando Alves`, Age: `45`, Phone: `+55-27-99555-1234`, Check `Hypertension`.
  * Click **Save Patient**.
* **Action:** Click **Doctor Directory**, point out Dr. Beatriz Silva (`Available`), and click **Toggle Availability** to demonstrate status switching.
* **Presenter Script:**
  > "In Patient Management, we demonstrate full Object-Oriented CRUD operations with input validation. If I enter an age of -5 or a duplicate ID, our custom exception hierarchy catches it immediately. Notice the chronic illness flags.
  > 
  > In Doctor Management, we can toggle physician availability between 'Available' and 'On Leave'. Unavailable physicians are automatically disabled in our appointment scheduling wizard."

---

### [05:00 – 06:30] Step 7, 8 & 9: Appointment Booking & Live ML Risk Preview
* **Action:** Click **Appointment Management**, click **Book New Appointment**.
  * Select Patient: `Fernando Alves (ID: 1005)`
  * Select Doctor: `Dr. Ricardo Mendes (General Medicine)`
  * Select Date: Pick a date **10 days in the future**.
  * Select Time: `11:00 AM`.
  * **Highlight:** Point out the **ML Risk Assessment Preview** box at the bottom of the modal updating dynamically!
* **Presenter Script:**
  > "Now observe our core innovation: Intelligent Booking with Live ML Prediction. As I select the patient, doctor, and consultation date, our Java Event Listeners extract a 6-feature vector: lead time in days, day of week, booking hour, same-day flag, and patient age. 
  > 
  > The loaded Weka Random Forest computes the class probability distribution instantly. Because this appointment has a 10-day lead time, the model flags it with an estimated no-show risk above 50%—categorized as **HIGH RISK**. The system recommends: 'Dedicated Front-Desk Phone Call'.
  > 
  > When I click **Confirm & Book**, the appointment is saved to our atomic CSV repository, and a formatted queue token (`Q-001`) is issued automatically."

---

### [06:30 – 07:30] Step 10 & 11: Patient Check-In & Fair FIFO Smart Queue
* **Action:** Go to **Appointment Management**, find today's booking, and click **Check-In Patient**.
* **Action:** Click **Smart Queue** on the sidebar.
* **Presenter Script:**
  > "When appointment day arrives and the patient walks into the clinic, reception clicks 'Check In'. The patient enters the Smart Queue waiting room.
  > 
  > Please note our **Ethical Fairness Principle**: even if a patient was predicted as High Risk, their queue sequence is governed strictly by their check-in arrival timestamp (FIFO). High-risk patients are never demoted or denied service. 
  > 
  > When the physician is ready, clicking **Call to Consultation Room** advances the state to `IN_CONSULTATION`. When the visit ends, clicking **Complete Consultation** updates the outcome to `ATTENDED`."

---

### [07:30 – 08:30] Step 12: Proactive Reminder Simulation & Persistence
* **Action:** Click **Reminder Management**.
  * Click **Simulate Send All Pending**.
  * Point out the delivery status updating to `SENT` and timestamp logging.
* **Presenter Script:**
  > "In Reminder Management, front-desk staff can view all upcoming appointments stratified by risk tier. Staff can simulate dispatching automated SMS for Low-Risk patients or logging personal phone calls for High-Risk patients. Every transaction is stamped with an exact dispatch time and persisted to `data/app/reminders.csv`."

---

### [08:30 – 09:30] Step 13 & 14: Java OOP Concepts & Architecture
* **Action:** Click **About & Help** or refer to project architecture diagrams.
* **Presenter Script:**
  > "From a software engineering perspective, ClinicFlow satisfies every required Java concept:
  > 1. **Inheritance:** `Patient` and `Doctor` extend abstract `Person`.
  > 2. **Encapsulation:** Immutable `PredictionResult` protects ML probabilities from tampering.
  > 3. **Polymorphism:** Generic `Repository<T, ID>` decouples services from CSV storage.
  > 4. **Collections:** Thread-safe `ConcurrentHashMap` and `ArrayList` handle fast indexing.
  > 5. **Concurrency:** `SwingWorker` loads the 233.9 MB Weka model asynchronously in the background so the UI never freezes.
  > 6. **Robust File I/O:** Atomic file replacement (`.tmp` to permanent move) prevents data corruption."

---

### [09:30 – 10:00] Step 15: Limitations, Future Work & Conclusion
* **Action:** Return to **Dashboard**.
* **Presenter Script:**
  > "In conclusion, ClinicFlow achieves a genuine 32.93% no-show recall on held-out historical data without synthetic shortcuts or exaggerated claims. 
  > 
  > As an honest academic limitation, the current model reflects Brazilian public clinics, and retraining on local Indian hospital data will be necessary for domestic deployment. 
  > 
  > All 41 unit and integration tests pass with 100% success. Thank you, and I welcome your questions."
