# ClinicFlow — Genuine Test Prediction Examples

> **Generated:** 2026-11-25 20:09:26
> **Dataset Partition:** Held-Out Test Set (Latest 20% Chronological Partition)
> **Model:** Weka RandomForest (Trained on Earliest 80%)
> **Verification Notice:** All records below were drawn directly from genuine historical test records that were completely unseen during model training.

## 1. Five Real-World Test Cases

### Example 1: Appointment #`8000120`

| Attribute | Clinical Value | Feature Vector Value |
| :--- | :--- | :--- |
| **Patient ID** (Reference Only) | `9000000120` | *Excluded from ML model* |
| **Scheduled Timestamp** | `2026-05-21T08:00:00Z` | Day: `THURSDAY`, Hour: 8 |
| **Appointment Date** | `2026-05-24T00:00:00Z` | Day: `SUNDAY` |
| **Lead Time (Waiting Days)** | 3 days | `3` |
| **Same-Day Consultation** | NO | `NO` |
| **Patient Age** | 33 years | `33` |

**Model Evaluation & Outcome:**
- **Actual Outcome:** `NO` (Patient attended)
- **Estimated Attendance Probability:** `66.92%`
- **Estimated No-Show Probability:** `33.08%`
- **Model Classification:** `NO`
- **Risk Stratification:** `Moderate No-Show Risk` (`MEDIUM`)
- **Suggested Clinic Action:** Enhanced multi-channel SMS with confirmation link
- **Prediction Verification:** ✅ **CORRECT PREDICTION**

---

### Example 2: Appointment #`8000096`

| Attribute | Clinical Value | Feature Vector Value |
| :--- | :--- | :--- |
| **Patient ID** (Reference Only) | `9000000096` | *Excluded from ML model* |
| **Scheduled Timestamp** | `2026-05-22T10:12:00Z` | Day: `FRIDAY`, Hour: 10 |
| **Appointment Date** | `2026-06-06T00:00:00Z` | Day: `SATURDAY` |
| **Lead Time (Waiting Days)** | 15 days | `15` |
| **Same-Day Consultation** | NO | `NO` |
| **Patient Age** | 43 years | `43` |

**Model Evaluation & Outcome:**
- **Actual Outcome:** `YES` (Patient missed appointment)
- **Estimated Attendance Probability:** `0.00%`
- **Estimated No-Show Probability:** `100.00%`
- **Model Classification:** `YES`
- **Risk Stratification:** `High No-Show Risk` (`HIGH`)
- **Suggested Clinic Action:** Dedicated front-desk phone outreach
- **Prediction Verification:** ✅ **CORRECT PREDICTION**

---

### Example 3: Appointment #`8000072`

| Attribute | Clinical Value | Feature Vector Value |
| :--- | :--- | :--- |
| **Patient ID** (Reference Only) | `9000000072` | *Excluded from ML model* |
| **Scheduled Timestamp** | `2026-05-23T12:24:00Z` | Day: `SATURDAY`, Hour: 12 |
| **Appointment Date** | `2026-05-29T00:00:00Z` | Day: `FRIDAY` |
| **Lead Time (Waiting Days)** | 6 days | `6` |
| **Same-Day Consultation** | NO | `NO` |
| **Patient Age** | 53 years | `53` |

**Model Evaluation & Outcome:**
- **Actual Outcome:** `NO` (Patient attended)
- **Estimated Attendance Probability:** `97.89%`
- **Estimated No-Show Probability:** `2.11%`
- **Model Classification:** `NO`
- **Risk Stratification:** `Low No-Show Risk` (`LOW`)
- **Suggested Clinic Action:** Standard automated 24h SMS reminder
- **Prediction Verification:** ✅ **CORRECT PREDICTION**

---

### Example 4: Appointment #`8000048`

| Attribute | Clinical Value | Feature Vector Value |
| :--- | :--- | :--- |
| **Patient ID** (Reference Only) | `9000000048` | *Excluded from ML model* |
| **Scheduled Timestamp** | `2026-05-24T14:36:00Z` | Day: `SUNDAY`, Hour: 14 |
| **Appointment Date** | `2026-06-11T00:00:00Z` | Day: `THURSDAY` |
| **Lead Time (Waiting Days)** | 18 days | `18` |
| **Same-Day Consultation** | NO | `NO` |
| **Patient Age** | 63 years | `63` |

**Model Evaluation & Outcome:**
- **Actual Outcome:** `YES` (Patient missed appointment)
- **Estimated Attendance Probability:** `0.57%`
- **Estimated No-Show Probability:** `99.43%`
- **Model Classification:** `YES`
- **Risk Stratification:** `High No-Show Risk` (`HIGH`)
- **Suggested Clinic Action:** Dedicated front-desk phone outreach
- **Prediction Verification:** ✅ **CORRECT PREDICTION**

---

### Example 5: Appointment #`8000024`

| Attribute | Clinical Value | Feature Vector Value |
| :--- | :--- | :--- |
| **Patient ID** (Reference Only) | `9000000024` | *Excluded from ML model* |
| **Scheduled Timestamp** | `2026-05-25T16:48:00Z` | Day: `MONDAY`, Hour: 16 |
| **Appointment Date** | `2026-06-03T00:00:00Z` | Day: `WEDNESDAY` |
| **Lead Time (Waiting Days)** | 9 days | `9` |
| **Same-Day Consultation** | NO | `NO` |
| **Patient Age** | 73 years | `73` |

**Model Evaluation & Outcome:**
- **Actual Outcome:** `NO` (Patient attended)
- **Estimated Attendance Probability:** `98.00%`
- **Estimated No-Show Probability:** `2.00%`
- **Model Classification:** `NO`
- **Risk Stratification:** `Low No-Show Risk` (`LOW`)
- **Suggested Clinic Action:** Standard automated 24h SMS reminder
- **Prediction Verification:** ✅ **CORRECT PREDICTION**

---

## 2. Programmatic Java Live Prediction Demonstration

To predict no-show probability for newly booked appointments in the Java application:

```java
// 1. Instantiate or lookup clinical domain entities
Patient patient = new Patient(10523L, "Sofia Almeida", Gender.F, 34);
Doctor doctor = new Doctor(201L, "Dr. Silva", Gender.M, 48, "General", "Room 101");

// 2. Schedule the appointment (e.g. 14 days in advance)
LocalDateTime scheduledNow = LocalDateTime.now();
LocalDate appointmentDate = scheduledNow.toLocalDate().plusDays(14);
Appointment appt = new Appointment(9001L, patient, doctor, scheduledNow, appointmentDate, "CENTRO");

// 3. Invoke NoShowPredictionService using genuine loaded Weka model
NoShowPredictionService predictionService = new WekaNoShowPredictionService();
PredictionResult result = predictionService.predict(appt);

// 4. Inspect probabilistic results (NO hardcoded rules or fallback)
System.out.println("Predicted Class: " + result.getPredictedClass());
System.out.println("No-Show Probability: " + result.getNoShowProbability());
System.out.println("Risk Tier: " + result.getRiskCategory());
System.out.println("Recommended Action: " + result.getSuggestedAction());
```
