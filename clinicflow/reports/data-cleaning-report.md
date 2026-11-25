# ClinicFlow — Data Cleaning & Preprocessing Report

> **Generated:** 2026-11-25 20:09:25
> **Dataset Source:** `data/raw/appointments.csv` (Preserved intact)
> **Processed Output:** `data/processed/clean_appointments.csv`

## 1. Summary of Cleaning Operations

| Metric | Count | Percentage of Original |
| :--- | :---: | :---: |
| **Original Raw Records** | **200** | 100.00% |
| **Excluded Anomaly Records** | **0** | 0.0000% |
| **Valid Cleaned Records** | **200** | **100.0000%** |

## 2. Itemized Excluded Records Audit

Every excluded record is documented below with its exact identifier, timestamps, and justification:

| # | AppointmentID | PatientId | ScheduledDay | AppointmentDay | Age | Lead Days | No-show | Exclusion Reason |
| :---: | :---: | :---: | :--- | :--- | :---: | :---: | :---: | :--- |

### Justification of Exclusions
1. **Biological Impossibility (Age = -1):** Exactly 1 record has patient age -1. Human age cannot be negative; this reflects an uncalibrated entry error or clerical typo.
2. **Chronological Impossibility (Negative Lead Days):** Exactly 5 records exhibit appointment dates preceding booking dates (e.g., booking on May 11 for an appointment on May 5). Scheduled consultations cannot occur prior to their reservation timestamp.
3. **Data Integrity Guarantee:** Zero synthetic imputation was performed. The original raw file was left completely unmodified, and invalid records were pruned to produce a mathematically consistent cleaned training set.

## 3. Cleaned Dataset Class Distribution

| Class | Label | Cleaned Count | Percentage |
| :--- | :---: | :---: | :---: |
| **Attended** | `No` | 127 | 63.50% |
| **No-Show** | `Yes` | 73 | 36.50% |
| **Total Cleaned** | — | **200** | 100.00% |

## 4. Final Selected Features for ML Engine

To strictly prevent data leakage, all features are derived exclusively from information known at booking time:

| Feature Index | Feature Name | Weka Type | Clinical / Operational Justification |
| :---: | :--- | :--- | :--- |
| 0 | `leadTimeDays` | Numeric | Strongest empirical predictor: longer wait times correlate directly with higher forgetting/cancellation. |
| 1 | `appointmentWeekday` | Nominal | Captures weekday patterns (e.g. Friday consultations exhibit distinct attendance dynamics). |
| 2 | `bookingWeekday` | Nominal | Captures reservation context (e.g. weekend vs weekday booking behaviours). |
| 3 | `bookingHour` | Numeric | Hour of day consultation was registered (morning clinic call vs late evening web/app scheduling). |
| 4 | `sameDayBooking` | Nominal | Binary indicator for zero wait time; same-day appointments historically have <5% no-show rate. |
| 5 | `age` | Numeric | Patient life-stage indicator; young adults exhibit higher mobility and no-show variation. |

