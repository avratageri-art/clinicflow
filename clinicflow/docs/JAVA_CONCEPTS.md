# ClinicFlow — Java Programming Concepts Implementation

This document provides explicit code evidence demonstrating how mandatory Java programming concepts are deeply incorporated into the ClinicFlow architecture.

---

## 1. Classes and Objects

* **`Patient` & `Doctor`:** Domain entities representing clinic participants.
* **`Appointment`:** Core domain object modeling consultations, holding patient associations and scheduling dates.
* **`AppointmentService`:** Orchestrator object managing clinic workflows.
* **`PredictionResult`:** Value object encapsulating ML predictions.

**Code Location:** `src/main/java/com/clinicflow/model/`

---

## 2. Constructors

* **Overloaded Constructors:**
  * `Patient(Long id, String name, Gender gender, int age)`: Convenience constructor for standard patient.
  * `Patient(Long id, String name, Gender gender, int age, boolean scholarship, boolean hipertension, ...)`: Full clinical history constructor.
  * `Appointment(Long id, Patient patient, Doctor doctor, LocalDateTime scheduledDay, LocalDate appointmentDay, String neighbourhood)`: New appointment booking constructor.
* **Constructor Validation:** Constructors immediately validate null references, age limits, and handicap values to guarantee valid state upon instantiation.

**Code Location:** [Patient.java](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/model/Patient.java), [Appointment.java](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/model/Appointment.java)

---

## 3. Encapsulation

* All class fields (`id`, `name`, `gender`, `age`, `scheduledDay`, `appointmentDay`, `predictionResult`) are declared `private` or `private final`.
* Access is provided via strictly validated getters and controlled setters.
* Mutable collections are protected with defensive copies (`Collections.unmodifiableList` or `new ArrayList<>(storage.values())`).

**Code Location:** [Person.java](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/model/Person.java), [Appointment.java](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/model/Appointment.java)

---

## 4. Inheritance and Interfaces

* **Interface Abstraction:**
  * `Identifiable<ID>`: Generic contract for entities with unique keys.
  * `Repository<T, ID>`: Generic contract for data access.
  * `PatientRepository` and `AppointmentRepository`: Domain-specific repository contracts.
* **Class Inheritance:**
  * `Person` (abstract class) is extended by both `Patient` and `Doctor`.
  * Demonstrates polymorphic behaviour through `person.getRoleDescription()`.

**Code Location:** [Identifiable.java](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/model/Identifiable.java), [Person.java](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/model/Person.java), [Repository.java](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/repository/Repository.java)

---

## 5. Collections Framework

* **`HashMap<Long, T>`:** Used in repositories for $O(1)$ fast lookups by entity ID.
* **`ArrayList<T>`:** Used for indexed result sets, search filtering, and record iterations.
* **`LinkedHashMap<String, Integer>`:** Used in dataset profiling to preserve column order and top 10 rankings.
* **`HashSet<String>`:** Used in dataset inspection to verify uniqueness and detect duplicate appointment IDs.

**Code Location:** [InMemoryPatientRepository.java](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/repository/InMemoryPatientRepository.java), [DatasetInspector.java](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/ml/DatasetInspector.java)

---

## 6. Exception Handling

A custom exception hierarchy extends `RuntimeException`:
* `ClinicFlowException`: Base application exception.
  * `ValidationException`: Thrown on clinical rule violations (e.g., appointment date before scheduling date, invalid age).
  * `DatasetLoadingException`: Thrown when CSV files are missing, corrupted, or unreadable.
  * `EntityNotFoundException`: Thrown on patient or appointment lookup failure.
  * `DuplicateEntityException`: Thrown on primary key conflicts.

**Code Location:** `src/main/java/com/clinicflow/util/`

---

## 7. File Handling

* **CSV File Reading:** Leverages `BufferedReader` with UTF-8 encoding and Apache Commons CSV `CSVParser` to parse the 110,527 records safely without naive string splits.
* **Markdown Report Writing:** Outputs statistical profiles to `reports/dataset-profile.md` using `FileWriter`.
* **Weka Model Persistence (Stage 2):** Uses `weka.core.SerializationHelper` to save and reload the trained model.

**Code Location:** [CsvDatasetLoader.java](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/ml/CsvDatasetLoader.java), [DatasetProfile.java](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/ml/DatasetProfile.java)

---

## 8. Java & Machine Learning Integration

* Weka 3.8.6 is directly embedded as a Maven dependency within the Java application.
* Preprocessing, training, evaluation, and runtime inference are executed entirely in Java bytecode without Python or external process bridges.
