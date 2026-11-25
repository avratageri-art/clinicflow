# ClinicFlow Architecture & Workflow Diagrams

All diagrams below reflect the actual implementation in `clinicflow/src/main/java/com/clinicflow/`.

## 1. System Architecture

```mermaid
flowchart TD
    User["Clinic Staff / User"] --> Main["Main.java\n(Swing GUI or --cli)"]
    Main --> App["ClinicFlowApp (JFrame)\n8 Navigation Panels"]

    subgraph UI["UI Layer (com.clinicflow.ui)"]
        App --> Dash["DashboardPanel"]
        App --> PatUI["PatientManagementPanel"]
        App --> DocUI["DoctorManagementPanel"]
        App --> ApptUI["AppointmentManagementPanel"]
        App --> QueueUI["QueueManagementPanel"]
        App --> RemUI["ReminderManagementPanel"]
        App --> MLUI["ModelInsightsPanel\n(+ Async Train Button)"]
    end

    subgraph Services["Service Layer (com.clinicflow.service)"]
        PatSvc["PatientService"]
        DocSvc["DoctorService"]
        ApptSvc["AppointmentService"]
        RemSvc["ReminderService"]
        PredSvc["WekaNoShowPredictionService"]
        InsSvc["ModelInsightsService"]
    end

    subgraph ML["ML Engine (com.clinicflow.ml)"]
        Trainer["NoShowModelTrainer"]
        Preproc["DataPreprocessor"]
        Feat["FeatureExtractor"]
        Eval["ModelEvaluator"]
        Pred["NoShowPredictor"]
        ModRepo["ModelRepository"]
    end

    subgraph Storage["Persistence Layer (com.clinicflow.repository)"]
        CsvPat["CsvPatientRepository"]
        CsvDoc["CsvDoctorRepository"]
        CsvAppt["CsvAppointmentRepository"]
        CsvRem["CsvReminderRepository"]
    end

    PatUI --> PatSvc --> CsvPat
    DocUI --> DocSvc --> CsvDoc
    ApptUI --> ApptSvc
    QueueUI --> ApptSvc
    RemUI --> RemSvc --> CsvRem
    ApptSvc --> CsvAppt
    ApptSvc --> PredSvc --> Pred
    MLUI --> InsSvc --> ModRepo
    MLUI --> Trainer
    Trainer --> Preproc --> Feat
    Trainer --> Eval
    Trainer --> ModRepo
```

## 2. Appointment Booking & Live ML Inference Workflow

```mermaid
sequenceDiagram
    actor Staff as Clinic Staff
    participant UI as AppointmentBookingDialog
    participant Svc as AppointmentService
    participant Val as DataValidator
    participant ML as WekaNoShowPredictionService
    participant Repo as CsvAppointmentRepository

    Staff->>UI: Select Patient, Doctor, Date, Neighbourhood
    UI->>Svc: bookAppointment(...)
    Svc->>Val: validateAppointment(...)
    Svc->>ML: predict(appointment)
    alt Model Loaded in Memory
        ML-->>Svc: PredictionResult(prob, RiskCategory, ReminderAction)
    else Model Unavailable
        ML-->>Svc: PredictionResult.unavailable()
    end
    Svc->>Repo: save(appointment)
    Repo-->>Svc: Atomic CSV write (data/app/appointments.csv)
    Svc-->>UI: Booked Appointment + Risk Badge
    UI-->>Staff: Display Risk Tier & Suggested Action
```

## 3. ML Training & Evaluation Pipeline

```mermaid
flowchart LR
    Raw["data/raw/appointments.csv\n(110,527 records)"] --> Clean["DataPreprocessor.cleanDataset()\n6 anomalies removed"]
    Clean --> CleanCsv["data/processed/clean_appointments.csv\n(110,521 records)"]
    CleanCsv --> Split["Chronological 80:20 Split\n(Sorted by ScheduledDay)"]
    Split --> Train["Train Set (80%: 88,417)"]
    Split --> Test["Test Set (20%: 22,104)"]

    Train --> ZeroR["ZeroR Baseline"]
    Train --> StdRF["Standard RandomForest\n(100 trees, seed=42)"]
    Train --> BalRF["FilteredClassifier\nClassBalancer + RandomForest"]

    ZeroR --> Eval["ModelEvaluator\n(Evaluated on untouched Test Set)"]
    StdRF --> Eval
    BalRF --> Eval

    Eval --> ModelOut["models/no-show-random-forest.model\nmodels/model-metadata.ser\nreports/confusion-matrix.csv"]
```

## 4. Domain Model & Repository Design

```mermaid
classDiagram
    class Person {
        <<abstract>>
        -long id
        -String name
        -Gender gender
        -int age
    }
    class Patient {
        -boolean scholarship
        -boolean hypertension
        -boolean diabetes
        -boolean alcoholism
        -int handicapLevel
    }
    class Doctor {
        -String specialization
        -String roomNumber
        -boolean available
    }
    class Appointment {
        -long id
        -Patient patient
        -Doctor doctor
        -LocalDateTime scheduledDay
        -LocalDate appointmentDay
        -String neighbourhood
        -AttendanceStatus status
        -String queueToken
        -PredictionResult predictionResult
    }
    class ReminderRecord {
        -long id
        -long appointmentId
        -ReminderAction action
        -LocalDateTime sentAt
    }

    Person <|-- Patient
    Person <|-- Doctor
    Appointment --> Patient
    Appointment --> Doctor
    ReminderRecord --> Appointment
```
