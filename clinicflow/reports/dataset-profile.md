# ClinicFlow — Dataset Inspection & Quality Profile

> **Report Generated:** 2026-11-22 19:40:32
> **Dataset Target:** Medical Appointment No-Shows (`data/raw/appointments.csv`)
> **Status:** Genuine historical healthcare dataset verified and profiled.

## 1. Dataset Integrity & Overview

| Metric | Value |
| :--- | :--- |
| **File Path** | `D:\JAVA_ML\clinicflow\data\raw\appointments.csv` |
| **File Size** | 10,739,535 bytes (10.24 MB) |
| **Total Records** | **110,527** |
| **Total Columns** | **14** |
| **Unique Patients** | 62,299 |
| **Duplicate Appointment IDs** | 0 (0% duplicates) |

## 2. Column Specification & Missing Value Audit

| Index | Column Name | Inferred Java Type | Missing Values | Quality Status |
| :---: | :--- | :--- | :---: | :--- |
| 1 | `PatientId` | Long | 0 | ✅ Complete (0 nulls) |
| 2 | `AppointmentID` | Long | 0 | ✅ Complete (0 nulls) |
| 3 | `Gender` | String (Nominal) | 0 | ✅ Complete (0 nulls) |
| 4 | `ScheduledDay` | LocalDateTime (ISO-8601) | 0 | ✅ Complete (0 nulls) |
| 5 | `AppointmentDay` | LocalDate (ISO-8601) | 0 | ✅ Complete (0 nulls) |
| 6 | `Age` | int (Integer) | 0 | ✅ Complete (0 nulls) |
| 7 | `Neighbourhood` | String (Nominal) | 0 | ✅ Complete (0 nulls) |
| 8 | `Scholarship` | boolean (Binary 0/1) | 0 | ✅ Complete (0 nulls) |
| 9 | `Hipertension` | boolean (Binary 0/1) | 0 | ✅ Complete (0 nulls) |
| 10 | `Diabetes` | boolean (Binary 0/1) | 0 | ✅ Complete (0 nulls) |
| 11 | `Alcoholism` | boolean (Binary 0/1) | 0 | ✅ Complete (0 nulls) |
| 12 | `Handcap` | int (Integer) | 0 | ✅ Complete (0 nulls) |
| 13 | `SMS_received` | boolean (Binary 0/1) | 0 | ✅ Complete (0 nulls) |
| 14 | `No-show` | String (Nominal) | 0 | ✅ Complete (0 nulls) |

## 3. Target Variable Distribution (`No-show`)

| Class | Dataset Label | Meaning | Count | Percentage |
| :--- | :---: | :--- | :---: | :---: |
| **Attended** | `No` | Patient showed up to appointment | 88,208 | 79.81% |
| **No-Show** | `Yes` | Patient failed to attend | 22,319 | 20.19% |
| **Total** | — | Combined appointments | 110,527 | 100.00% |

> **Class Imbalance Note:** The minority class (No-Shows) comprises **~20.19%** of observations. A naive majority-class classifier predicting 'Attended' for all cases would achieve ~79.81% accuracy while identifying zero no-shows. Therefore, **Recall, Precision, F1-Score, and ROC-AUC** are strictly required alongside Accuracy.

## 4. Demographic Distribution

### Age
- **Minimum Age:** -1 (Contains 1 record with age -1, an identifiable data entry anomaly to filter)
- **Maximum Age:** 115
- **Mean Age:** 37.09 years
- **Centenarians (Age > 100):** 7 records

### Gender
- **Female (`F`):** 71,840 (65.00%)
- **Male (`M`):** 38,687 (35.00%)

## 5. Temporal & Waiting Time Analysis

| Dimension | Earliest | Latest |
| :--- | :--- | :--- |
| **Scheduled Day** | `2015-11-10T07:13:56` | `2016-06-08T20:07:23` |
| **Appointment Day** | `2016-04-29` | `2016-06-08` |

- **Minimum Lead Days:** -6 (Contains 5 records where appointment date preceded scheduled date, requiring removal during preprocessing)
- **Maximum Lead Days:** 179 days
- **Average Lead Days:** 10.18 days
- **Same-Day Bookings (Lead = 0):** 38,563 (34.89%)

## 6. Clinical & Socioeconomic Indicators

| Feature | True (1) Count | Percentage | False (0) Count | Percentage |
| :--- | :---: | :---: | :---: | :---: |
| **Scholarship (Bolsa Família)** | 10,861 | 9.83% | 99,666 | 90.17% |
| **Hipertension** | 21,801 | 19.72% | 88,726 | 80.28% |
| **Diabetes** | 7,943 | 7.19% | 102,584 | 92.81% |
| **Alcoholism** | 3,360 | 3.04% | 107,167 | 96.96% |
| **SMS Received** | 35,482 | 32.10% | 75,045 | 67.90% |

### Handicap Severity Breakdown
| Level | Count | Percentage |
| :---: | :---: | :---: |
| Level 0 | 108,286 | 97.97% |
| Level 1 | 2,042 | 1.85% |
| Level 2 | 183 | 0.17% |
| Level 3 | 13 | 0.01% |
| Level 4 | 3 | 0.00% |

## 7. Geographic Distribution (Top 10 Neighbourhoods)

| Rank | Neighbourhood | Total Appointments | Percentage |
| :---: | :--- | :---: | :---: |
| 1 | `JARDIM CAMBURI` | 7,717 | 6.98% |
| 2 | `MARIA ORTIZ` | 5,805 | 5.25% |
| 3 | `RESISTÊNCIA` | 4,431 | 4.01% |
| 4 | `JARDIM DA PENHA` | 3,877 | 3.51% |
| 5 | `ITARARÉ` | 3,514 | 3.18% |
| 6 | `CENTRO` | 3,334 | 3.02% |
| 7 | `TABUAZEIRO` | 3,132 | 2.83% |
| 8 | `SANTA MARTHA` | 3,131 | 2.83% |
| 9 | `JESUS DE NAZARETH` | 2,853 | 2.58% |
| 10 | `BONFIM` | 2,773 | 2.51% |

## 8. Data Leakage Prevention & Preprocessing Guidelines for Stage 2

1. **Strict Exclusion of Target from Features:** `No-show` is strictly reserved as the supervision target variable.
2. **Exclusion of Identifiers:** `PatientId` and `AppointmentID` will be dropped from ML training features to prevent memorization/overfitting.
3. **Exclusion of `SMS_received` from Booking-Time Inference:** Because SMS notifications are sent days after initial booking, using `SMS_received` at booking time creates forward-looking leakage. It will be excluded from the booking-time feature vector.
4. **Cleaning Steps Identified:**
   - Remove the 1 record with `Age = -1`.
   - Remove the 5 records with negative waiting days (`AppointmentDay < ScheduledDay`).
5. **Chronological Evaluation Split:** The 80:20 train/test partition will be sorted chronologically by `AppointmentDay` to simulate true operational deployment.
