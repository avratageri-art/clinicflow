# ClinicFlow — Dataset Attribution & Provenance

## 1. Dataset Overview

* **Dataset Name:** Medical Appointment No Shows
* **Context:** Public health system (SUS) appointments across Vitória, Espírito Santo, Brazil.
* **Period Covered:** April 2016 – June 2016
* **Primary Target:** `No-show` (`No` = Patient attended; `Yes` = Patient did not show up)
* **Total Records:** 110,527 rows
* **Total Columns:** 14 original columns

---

## 2. Source Provenance

### Primary Original Source
* **Platform:** Kaggle
* **URL:** [https://www.kaggle.com/datasets/joniarroba/noshowappointments/data](https://www.kaggle.com/datasets/joniarroba/noshowappointments/data)
* **Author / Provider:** Joni Hoppen and Aquarela Advanced Analytics

### Verified Public CSV Mirror
* **Platform:** GitHub
* **URL:** [https://raw.githubusercontent.com/SharifAthar/Medical-Appointment-No-Shows---Full-Data/main/Medical%20Appointment%20No%20Shows.csv](https://raw.githubusercontent.com/SharifAthar/Medical-Appointment-No-Shows---Full-Data/main/Medical%20Appointment%20No%20Shows.csv)

### Local Storage & Data Separation Policy
* **Original Compressed Archive:** `D:\JAVA_ML\archive.zip` (Preserved intact; size 2,514,014 bytes)
* **Raw Unmodified CSV:** `data/raw/appointments.csv` (10,739,535 bytes, 110,527 records)
* **Processed Preprocessed CSV (Stage 2):** `data/processed/` (Cleaned copies with filtered anomalies)
* **Operational Application Records:** `data/app/` (Separate from historical training records)
* **Serialised Trained Model (Stage 2):** `models/`

---

## 3. Data Dictionary

| Column Name | Data Type | Description | Values / Range |
| :--- | :--- | :--- | :--- |
| `PatientId` | Numeric (Long) | Unique identifier for the patient | 6-15 digit identifier |
| `AppointmentID` | Numeric (Long) | Unique identifier for the specific appointment | 7-digit identifier |
| `Gender` | String / Nominal | Biological sex of the patient | `F` (Female), `M` (Male) |
| `ScheduledDay` | ISO-8601 Timestamp | Timestamp when the appointment was booked | e.g. `2016-04-29T18:38:08Z` |
| `AppointmentDay` | ISO-8601 Date | Scheduled date of the medical consultation | e.g. `2016-04-29T00:00:00Z` |
| `Age` | Integer | Age of patient in years | 0 to 115 (1 entry with -1) |
| `Neighbourhood` | String / Nominal | Clinic district/neighbourhood location | 81 distinct locations |
| `Scholarship` | Binary (0/1) | Welfare program enrollment (*Bolsa Família*) | `0` = No, `1` = Yes |
| `Hipertension` | Binary (0/1) | Diagnosed medical hypertension | `0` = No, `1` = Yes |
| `Diabetes` | Binary (0/1) | Diagnosed diabetes mellitus | `0` = No, `1` = Yes |
| `Alcoholism` | Binary (0/1) | History or diagnosis of alcoholism | `0` = No, `1` = Yes |
| `Handcap` | Integer | Degree of physical handicap | `0` (None) to `4` (Severe) |
| `SMS_received` | Binary (0/1) | Whether 1+ reminder SMS messages were sent | `0` = No, `1` = Yes |
| `No-show` | String / Nominal | **Target variable**: Did patient fail to show? | `No` (Attended), `Yes` (No-show) |

---

## 4. Strict Data Integrity Statement

1. All Machine Learning training and evaluation in ClinicFlow utilizes exclusively this genuine historical dataset.
2. Synthetic records are strictly forbidden from entering training or testing sets.
3. The raw file `data/raw/appointments.csv` remains completely unchanged.

---

## Synthetic Sample Dataset (sample_appointments.csv)

- **Purpose**: Reproducible, lightweight (< 25 KB, 200 synthetic records) demonstration and testing dataset for the smartcare-ai submission repository (keeping the repository strictly under the 10 MB hackathon limit).
- **Important Notice**: sample_appointments.csv is **100% synthetic** and **does not** represent real-world clinical accuracy or patient distributions. All empirical evaluation metrics reported in eports/ml-evaluation.md and eports/confusion-matrix.csv were computed on the full 110,527-record Kaggle Medical Appointment No-Shows dataset (ppointments.csv) in the original workspace.

### Running the Optional Genuine-Dataset Integration Test
`DatasetInspectionTest` is tagged with `@Tag("genuine-dataset")` and automatically skipped when `data/raw/appointments.csv` is absent. To execute it locally with the full Kaggle dataset:
```bash
# Either place the Kaggle CSV at clinicflow/data/raw/appointments.csv, or point CLINICFLOW_HOME to the original clinicflow/ folder:
CLINICFLOW_HOME=/path/to/JAVA_ML/clinicflow mvn test -Dtest=DatasetInspectionTest
```
