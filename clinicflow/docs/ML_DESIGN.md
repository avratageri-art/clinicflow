# ClinicFlow — Machine Learning System Design

## 1. Machine Learning Formulation

* **Task Type:** Supervised Binary Classification
* **Target Variable:** `noShow`
  * Class 0 (`NO`): Patient attended appointment (88,207 cleaned records, ~79.81%)
  * Class 1 (`YES`): Patient missed appointment — **Positive Class** (22,314 cleaned records, ~20.19%)
* **Algorithm Selected:** **Balanced Random Forest Classifier** (`weka.classifiers.meta.FilteredClassifier` wrapping `weka.classifiers.trees.RandomForest` and `weka.filters.supervised.instance.ClassBalancer`)
  * **Rationale:** Ensemble of 100 randomized decision trees. Excels with tabular clinical/operational data, captures non-linear relationships (e.g., interaction between waiting duration and age), resists overfitting, and outputs well-calibrated class probability distributions.

---

## 2. Dataset Attribution & Preprocessing

* **Dataset:** Medical Appointment No Shows (110,527 raw historical records)
* **Kaggle Source:** [joniarroba/noshowappointments](https://www.kaggle.com/datasets/joniarroba/noshowappointments/data)
* **Raw Unmodified CSV:** `data/raw/appointments.csv` (10,739,535 bytes, preserved strictly intact)
* **Preprocessed Clean CSV:** `data/processed/clean_appointments.csv` (110,521 valid records)
* **Excluded Anomalies:** Exactly 6 records pruned (1 entry with `Age = -1`; 5 entries where `AppointmentDay < ScheduledDay`). Zero synthetic records were created.

---

## 3. Booking-Time Feature Vector

All features are derived exclusively from attributes known at booking time:

1. `leadTimeDays` (Numeric): Calendar days between booking date and appointment date.
2. `appointmentWeekday` (Nominal): Day of week of consultation (`MONDAY` through `SUNDAY`).
3. `bookingWeekday` (Nominal): Day of week booking occurred (`MONDAY` through `SUNDAY`).
4. `bookingHour` (Numeric): Hour of day consultation was registered (0 to 23).
5. `sameDayBooking` (Nominal): Whether appointment is on same calendar day (`YES`, `NO`).
6. `age` (Numeric): Patient age in years (0 to 115).
7. `noShow` (Nominal): Target label (`NO`, `YES`).

### Strictly Excluded Features
* ❌ `No-show`: Supervised target label.
* ❌ `PatientId`: High-cardinality identifier; prevents individual memorization.
* ❌ `AppointmentID`: Transaction ID with zero predictive causality.
* ❌ `SMS_received`: **Critical Leakage Prevention:** SMS reminders are sent 24–48 hours prior to the consultation, which occurs days or weeks after initial booking. Including this at booking time creates forward-looking leakage.

---

## 4. Chronological 80:20 Evaluation Strategy

* **Chronological Sorting:** All records sorted in ascending order by `ScheduledDay`.
* **Training Partition (80%):** Earliest 88,417 records (2015-11-10 to 2016-05-25).
* **Testing Partition (20%):** Latest 22,104 records (2016-05-25 to 2016-06-08).
* **Evaluation Isolation:** The 22,104 test instances were strictly held out from training, hyperparameter tuning, and data rebalancing.

---

## 5. Measured Model Evaluation Results

| Candidate Model | Test Accuracy | Precision (YES) | Recall (YES) | F1-Score (YES) | ROC-AUC | Status |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **ZeroR (Majority-Class Baseline)** | **88.09%** | 0.0000 | 0.0000 | 0.0000 | 0.5000 | Naive Baseline |
| **Standard Random Forest** | 84.34% | **0.2432** | 0.1489 | 0.1847 | **0.6660** | Candidate 1 |
| **Balanced Random Forest (Selected)** | 77.50% | 0.2128 | **0.3293** | **0.2585** | 0.6658 | **Selected Optimal Model** |

### Selected Model Confusion Matrix
```
                         Predicted NO (Attended)    Predicted YES (No-Show)
Actual NO (Attended)              16,264                     3,207
Actual YES (No-Show)               1,766                       867
```

* **Selected Model Rationale:** The Balanced Random Forest more than doubles the Recall on missed appointments (from 14.89% to 32.93%), significantly improving the F1-score to 0.2585 while maintaining a solid ROC-AUC of 0.6658.
