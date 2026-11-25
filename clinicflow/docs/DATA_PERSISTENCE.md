# ClinicFlow — Data Persistence & Storage Architecture

## 1. Storage Overview

ClinicFlow implements a lightweight, file-backed persistent repository layer using **Apache Commons CSV** and **Java 17 NIO**.
All clinical records created during desktop operations are stored under `data/app/`.
The historical model training and evaluation datasets (`data/raw/appointments.csv` and `data/processed/clean_appointments.csv`) are strictly preserved as read-only assets and are never modified by the operational desktop application.

```
data/
├── raw/
│   └── appointments.csv           <- 110,527 raw historical Kaggle records (Read-Only)
├── processed/
│   └── clean_appointments.csv     <- 110,521 cleaned historical records (Read-Only)
└── app/                           <- Live Application Persistent Storage
    ├── patients.csv               <- Registered patients
    ├── doctors.csv                <- Registered medical staff and availability
    ├── appointments.csv           <- Scheduled appointments, tokens, ML risk scores, and statuses
    └── reminders.csv              <- Simulated reminder dispatch audit trail
```

---

## 2. File Schemas & Data Formats

### 2.1 Patients Storage (`data/app/patients.csv`)
| Column Name | Data Type | Description |
| :--- | :--- | :--- |
| `PatientId` | Numeric (`Long`) | Unique numeric patient identifier. |
| `Name` | Text (`String`) | Full name of the patient. |
| `Gender` | Text (`M` / `F`) | Biological gender. |
| `Age` | Numeric (`int`) | Patient age in years. |
| `ContactNumber` | Text (`String`) | Phone contact number. |
| `Scholarship` | Boolean (`true`/`false`) | Enrollment in welfare/scholarship program. |
| `Hipertension` | Boolean (`true`/`false`) | Hypertension diagnosis. |
| `Diabetes` | Boolean (`true`/`false`) | Diabetes diagnosis. |
| `Alcoholism` | Boolean (`true`/`false`) | Alcoholism history. |
| `Handicap` | Numeric (`int`) | Handicap severity score (0–4). |

### 2.2 Doctors Storage (`data/app/doctors.csv`)
| Column Name | Data Type | Description |
| :--- | :--- | :--- |
| `DoctorId` | Numeric (`Long`) | Unique doctor identifier. |
| `Name` | Text (`String`) | Attending physician name. |
| `Gender` | Text (`M` / `F`) | Gender. |
| `Age` | Numeric (`int`) | Age in years. |
| `Specialty` | Text (`String`) | Medical specialty (e.g. Cardiology, Endocrinology). |
| `RoomNumber` | Text (`String`) | Assigned consultation room. |
| `ContactNumber` | Text (`String`) | Direct physician phone contact. |
| `Available` | Boolean (`true`/`false`) | Duty status (whether doctor can accept bookings). |

### 2.3 Appointments Storage (`data/app/appointments.csv`)
| Column Name | Data Type | Description |
| :--- | :--- | :--- |
| `AppointmentId` | Numeric (`Long`) | Unique appointment identifier. |
| `PatientId` | Numeric (`Long`) | Foreign key referencing `patients.csv`. |
| `DoctorId` | Numeric (`Long`) | Foreign key referencing `doctors.csv`. |
| `ScheduledDay` | Timestamp (ISO-8601) | Moment the booking was registered. |
| `AppointmentDay` | Date (`YYYY-MM-DD`) | Date of scheduled consultation. |
| `AppointmentTime` | Time (`HH:mm:ss`) | Consultation time slot. |
| `QueueToken` | Text (`Q-XXX`) | Generated waiting room queue token. |
| `Neighbourhood` | Text (`String`) | Clinic district location. |
| `SmsReceived` | Boolean (`true`/`false`) | SMS delivery status flag. |
| `AttendanceStatus` | Enum (`String`) | `SCHEDULED`, `CHECKED_IN`, `IN_CONSULTATION`, `ATTENDED`, `NO_SHOW`, `CANCELLED`. |
| `PredictedClass` | Text (`YES`/`NO`/`UNAVAILABLE`) | Weka predicted outcome. |
| `NoShowProb` | Decimal (`double`) | Model-estimated no-show posterior probability. |
| `AttendanceProb` | Decimal (`double`) | Model-estimated attendance probability. |
| `RiskCategory` | Enum (`String`) | `LOW`, `MEDIUM`, `HIGH`. |
| `SuggestedAction` | Enum (`String`) | `STANDARD`, `ENHANCED_SMS`, `PHONE_CALL`. |
| `Notes` | Text (`String`) | Optional visit notes. |
| `CheckInTime` | Timestamp (ISO-8601) | Exact timestamp patient checked in at reception. |
| `ReminderStatus` | Text (`String`) | `PENDING` or `SENT (...)`. |

### 2.4 Reminders Storage (`data/app/reminders.csv`)
| Column Name | Data Type | Description |
| :--- | :--- | :--- |
| `ReminderId` | Numeric (`Long`) | Unique reminder transaction ID. |
| `AppointmentId` | Numeric (`Long`) | Foreign key referencing `appointments.csv`. |
| `PatientName` | Text (`String`) | Patient name for quick audit reference. |
| `PatientContact` | Text (`String`) | Recipient phone number. |
| `AppointmentDate` | Date (`YYYY-MM-DD`) | Consultation date. |
| `AppointmentTime` | Time (`HH:mm:ss`) | Consultation time. |
| `DoctorName` | Text (`String`) | Assigned doctor. |
| `RiskCategory` | Enum (`String`) | Model risk category at time of outreach. |
| `RecommendedAction` | Enum (`String`) | `STANDARD`, `ENHANCED_SMS`, `PHONE_CALL`. |
| `Status` | Text (`String`) | `PENDING` or `SENT`. |
| `SentTimestamp` | Timestamp (ISO-8601) | Timestamp of simulated reminder dispatch. |
| `Notes` | Text (`String`) | Simulation audit note. |

---

## 3. Concurrency, Atomicity & File Safety

1. **Thread-Safe Memory Caching**:
   Each CSV repository maintains an in-memory `ConcurrentHashMap<Long, Entity>` providing sub-millisecond retrieval.
2. **Atomic File Swapping**:
   When entities are saved, updated, or removed, the repository serializes the updated dataset to a temporary file (`.tmp`) and swaps it into place using `java.nio.file.Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`. This guarantees that partial writes or unexpected crashes never corrupt existing CSV files.
3. **Safe Initialization**:
   If an application data file does not exist, the repository creates the parent directory and initializes an empty CSV file with proper headers.
4. **UTF-8 Character Encoding**:
   All read and write streams enforce UTF-8 character encoding to preserve international accents and clinical notations cleanly across operating systems.
